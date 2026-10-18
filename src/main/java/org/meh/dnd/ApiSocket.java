package org.meh.dnd;

import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

@ServerEndpoint("/api/v1/events")
public class ApiSocket {
    @Inject
    RestServer server;

    @OnOpen
    public void open(Session session) {
        server.subscribeApi(session);
    }

    @OnMessage
    public void message(String body, Session session) {
        server.executeApiCommand(body, session);
    }

    @OnClose
    public void close(Session session) {
        server.unsubscribeApi(session);
    }
}
