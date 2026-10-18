package org.meh.dnd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ApiSocketTest {
    @Test
    void websocket_adapter_sends_snapshot_and_json_error_response() throws Exception {
        given().contentType("application/json").body("{\"name\":\"Socket Hero\",\"class\":\"fighter\"}")
                .when().post("/api/v1/character").then().statusCode(200);
        RecordingWebSocket listener = new RecordingWebSocket();
        WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(
                URI.create("ws://localhost:" + io.restassured.RestAssured.port + "/api/v1/events"), listener).join();
        try {
            JsonNode snapshot = new ObjectMapper().readTree(listener.nextMessage());
            assertEquals("state.snapshot", snapshot.path("type").asText());
            assertEquals("Socket Hero", snapshot.path("state").path("player").path("name").asText());
            socket.sendText("{\"type\":\"unknown\"}", true).join();
            assertFalse(new ObjectMapper().readTree(listener.nextMessage()).path("accepted").asBoolean());
        } finally { socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").join(); }
    }

    private static final class RecordingWebSocket implements WebSocket.Listener {
        private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        private final StringBuilder current = new StringBuilder();
        public void onOpen(WebSocket socket) { socket.request(1); }
        public java.util.concurrent.CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            current.append(data); if (last) { messages.add(current.toString()); current.setLength(0); }
            socket.request(1); return WebSocket.Listener.super.onText(socket, data, last);
        }
        String nextMessage() throws InterruptedException { return messages.poll(5, TimeUnit.SECONDS); }
    }
}
