package org.meh.dnd;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestMethodOrder(OrderAnnotation.class)
class HypermediaServerTest
{
    @BeforeAll
    static void resetInProcessGame() {
        io.quarkus.arc.ArcContainer container = Arc.container();
        if (container != null && container.isRunning()) {
            container.instance(GameEngine.class).get().clearGame();
        }
    }

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
        int stateStatus = given().when().get("/api/v1/state").then().extract().statusCode();
        assertTrue(stateStatus == 200 || stateStatus == 404);
        boolean gameExists = stateStatus == 200;

        given()
                .formParam("background", "")
                .formParam("place", "Dark Forest")
                .when().post("/quest")
                .then()
                .statusCode(200)
                .header("HX-Redirect", is(gameExists ? "/" : "/create.html"));
    }

    @Test
    @Order(3)
    void hypermedia_adapter_creates_a_character() {
        given().formParam("name", "Hypermedia Hero").formParam("class", "fighter")
                .formParam("level", 1).formParam("max_hp", 10).formParam("armor_class", 10)
                .formParam("strength", 10).formParam("constitution", 10).formParam("dexterity", 10)
                .formParam("intelligence", 10).formParam("wisdom", 10).formParam("charisma", 10)
                .formParam("background", "An adventure").formParam("place", "The forest")
                .formParam("actions", 1).formParam("bonus_actions", 1).formParam("speed", 30)
                .when().post("/character").then().statusCode(200);
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
                .body(org.hamcrest.Matchers.containsString("Hypermedia Hero"))
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
                    .formParam("action", "Rest")
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
                String firstLine = null;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (firstLine == null && System.nanoTime() < deadline) {
                    if (input.available() > 0) firstLine = reader.readLine();
                    else Thread.sleep(25);
                }
                assertTrue(firstLine != null && firstLine.startsWith("data:"));
            }
        } finally {
            scheduler.shutdownNow();
        }
    }

}
