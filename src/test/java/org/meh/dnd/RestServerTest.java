package org.meh.dnd;

import io.quarkus.test.junit.QuarkusTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
class RestServerTest
{
    @Test
    @Order(1)
    void testHelloEndpoint() {
        given()
          .when().get("/hello")
          .then()
             .statusCode(200)
             .body(is("Hello from Quarkus REST"));
    }

    @Test
    @Order(2)
    void creating_a_quest_without_a_character_redirects_to_character_creation() {
        given()
                .formParam("background", "")
                .formParam("place", "Dark Forest")
                .when().post("/quest")
                .then()
                .statusCode(200)
                .header("HX-Redirect", is("/create.html"));
    }

    @Test
    @Order(3)
    void api_character_creation_returns_a_renderer_neutral_state() {
        given()
                .contentType("application/json")
                .body("""
                        {
                          "name":"Godot Hero",
                          "class":"fighter",
                          "place":"The forest",
                          "background":"A shared adventure",
                          "maxHp":12,
                          "armorClass":15,
                          "strength":16
                        }
                        """)
                .when().post("/api/v1/character")
                .then()
                .statusCode(200)
                .body("player.name", is("Godot Hero"))
                .body("player.charClass", is("fighter"))
                .body("place", is("The forest"));
    }

    @Test
    @Order(4)
    void browser_contract_serves_html_and_local_assets() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.containsString("/assets/app.css"))
                .body(org.hamcrest.Matchers.containsString("htmx.org@2.0.1"))
                .body(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("cdn.tailwindcss.com")));

        given()
                .when().get("/assets/app.css")
                .then()
                .statusCode(200)
                .contentType(org.hamcrest.Matchers.containsString("text/css"))
                .body(org.hamcrest.Matchers.containsString(".bg-gray-900"));
    }

    @Test
    @Order(5)
    void browser_contract_renders_the_game_fragment() {
        given()
                .when().get("/game")
                .then()
                .statusCode(200)
                .body(org.hamcrest.Matchers.containsString("Godot Hero"))
                .body(org.hamcrest.Matchers.containsString("hx-ext=\"sse\""))
                .body(org.hamcrest.Matchers.containsString("sse-connect=\"/updates\""));
    }

    @Test
    @Order(6)
    void browser_contract_streams_html_updates_over_sse() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + io.restassured.RestAssured.port + "/updates"))
                .header("Accept", "text/event-stream")
                .build();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            CompletableFuture<HttpResponse<InputStream>> responseFuture = HttpClient.newHttpClient()
                    .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
            scheduler.schedule(() -> given()
                    .formParam("action", "Explore")
                    .formParam("info", "")
                    .when().post("/actions")
                    .then()
                    .statusCode(204), 250, TimeUnit.MILLISECONDS);

            HttpResponse<InputStream> response = responseFuture.get(5, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("content-type").orElse("")
                    .startsWith("text/event-stream"));
            try (InputStream input = response.body();
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(input, StandardCharsets.UTF_8))) {
                CompletableFuture<String> firstLine = CompletableFuture.supplyAsync(() -> {
                    try {
                        return reader.readLine();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                assertTrue(firstLine.get(5, TimeUnit.SECONDS).startsWith("data:"));
            }
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    @Order(7)
    void rest_contract_exposes_the_same_renderer_neutral_state() {
        given()
                .when().get("/api/v1/state")
                .then()
                .statusCode(200)
                .body("mode", is("EXPLORING"))
                .body("player.name", is("Godot Hero"))
                .body("player.charClass", is("fighter"));
    }

    @Test
    @Order(8)
    void websocket_contract_sends_snapshot_and_json_error_response() throws Exception {
        RecordingWebSocket listener = new RecordingWebSocket();
        WebSocket socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(
                        URI.create("ws://localhost:" + io.restassured.RestAssured.port + "/api/v1/events"),
                        listener)
                .join();
        try {
            JsonNode snapshot = new ObjectMapper().readTree(listener.nextMessage());
            assertEquals("dnd", snapshot.path("protocol").asText());
            assertEquals(1, snapshot.path("version").asInt());
            assertEquals("state.snapshot", snapshot.path("type").asText());
            assertEquals("Godot Hero", snapshot.path("state").path("player").path("name").asText());

            socket.sendText("{\"type\":\"unknown\"}", true).join();
            JsonNode error = new ObjectMapper().readTree(listener.nextMessage());
            assertTrue(!error.path("accepted").asBoolean());
            assertNotNull(error.path("error").asText());
        } finally {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join();
        }
    }

    private static final class RecordingWebSocket implements WebSocket.Listener {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder currentMessage = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public java.util.concurrent.CompletionStage<?> onText(
                WebSocket webSocket, CharSequence data, boolean last) {
            currentMessage.append(data);
            if (last) {
                messages.add(currentMessage.toString());
                currentMessage.setLength(0);
            }
            webSocket.request(1);
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        String nextMessage() throws InterruptedException {
            String message = messages.poll(5, TimeUnit.SECONDS);
            assertNotNull(message, "Timed out waiting for WebSocket message");
            return message;
        }
    }

}
