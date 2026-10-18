package org.meh.dnd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ServerEndpoint("/api/v1/events")
public class WebSocketServer {
    @Inject
    GameEngine engine;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<Session, java.util.function.Consumer<String>> subscriptions = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @OnOpen
    public void open(Session session) {
        java.util.function.Consumer<String> subscription = type -> send(session, type);
        subscriptions.put(session, subscription);
        engine.subscribeApiUpdates(subscription);
        send(session, "state.snapshot");
    }

    @OnMessage
    public void message(String body, Session session) {
        try {
            JsonNode command = mapper.readTree(body);
            ApiCommand.execute(command, engine);
            engine.notifyUpdate("command.accepted");
        } catch (Exception e) {
            sendRaw(session, new ApiModels.CommandResult(false, e.getMessage(), engine.apiState()));
        }
    }

    @OnClose
    public void close(Session session) {
        java.util.function.Consumer<String> subscription = subscriptions.remove(session);
        if (subscription != null) engine.unsubscribeApiUpdates(subscription);
    }

    private void send(Session session, String type) { sendRaw(session,
            new ApiModels.Event("dnd", 1, sequence.incrementAndGet(), type, engine.apiState())); }
    private void sendRaw(Session session, Object value) {
        if (!session.isOpen()) return;
        try { session.getAsyncRemote().sendText(mapper.writeValueAsString(value)); }
        catch (IOException ignored) { }
    }
}
