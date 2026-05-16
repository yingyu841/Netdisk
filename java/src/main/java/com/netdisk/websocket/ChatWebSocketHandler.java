package com.netdisk.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netdisk.service.ChatMessagePushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger("chat.websocket");
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final ChatMessagePushService chatMessagePushService;

    public ChatWebSocketHandler(ObjectMapper objectMapper, @Lazy ChatMessagePushService chatMessagePushService) {
        this.objectMapper = objectMapper;
        this.chatMessagePushService = chatMessagePushService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = getUserId(session);
        if (userId != null) {
            sessions.put(userId, session);
            log.info("WebSocket connected: userId={}, sessionId={}", userId, session.getId());
        } else {
            log.warn("WebSocket connection rejected: no userId in session");
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = getUserId(session);
        if (userId != null) {
            sessions.remove(userId);
            log.info("WebSocket disconnected: userId={}, sessionId={}", userId, session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Received WebSocket message: {}", payload);

        try {
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String type = (String) data.get("type");

            if ("ping".equals(type)) {
                session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
            }
        } catch (Exception e) {
            log.warn("Failed to handle WebSocket message: {}", e.getMessage());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String userId = getUserId(session);
        log.error("WebSocket transport error: userId={}, error={}", userId, exception.getMessage());
        sessions.remove(userId);
        session.close(CloseStatus.SERVER_ERROR);
    }

    public void pushToUser(String userId, Map<String, Object> message) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(message);
                synchronized (session) {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                        log.debug("Pushed message to userId={}: {}", userId, json);
                    }
                }
            } catch (IOException e) {
                log.error("Failed to push message to userId={}: {}", userId, e.getMessage());
            }
        }
    }

    public void pushToUsers(Map<String, Map<String, Object>> userMessages) {
        for (Map.Entry<String, Map<String, Object>> entry : userMessages.entrySet()) {
            pushToUser(entry.getKey(), entry.getValue());
        }
    }

    private String getUserId(WebSocketSession session) {
        return (String) session.getAttributes().get("userId");
    }
}
