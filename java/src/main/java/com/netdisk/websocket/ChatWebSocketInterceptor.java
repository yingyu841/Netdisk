package com.netdisk.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Set;

@Component
public class ChatWebSocketInterceptor implements HandshakeInterceptor {
    private static final Logger log = LoggerFactory.getLogger("chat.websocket.interceptor");
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ChatWebSocketInterceptor(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public ChatWebSocketInterceptor() {
        this.redisTemplate = null;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        String query = request.getURI().getQuery();
        if (query == null || !query.contains("token=")) {
            log.warn("WebSocket handshake rejected: no token in query");
            return false;
        }

        String token = extractToken(query);
        if (token == null || token.trim().isEmpty()) {
            log.warn("WebSocket handshake rejected: empty token");
            return false;
        }

        String userId = validateToken(token);
        if (userId == null) {
            log.warn("WebSocket handshake rejected: invalid token");
            return false;
        }

        attributes.put("userId", userId);
        attributes.put("token", token);
        log.info("WebSocket handshake accepted: userId={}", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractToken(String query) {
        String[] params = query.split("&");
        for (String param : params) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }

    private String validateToken(String token) {
        if (redisTemplate == null) {
            return extractUserIdFromToken(token);
        }
        try {
            String cacheKey = "chat:ws:token:user:";
            String prefix = token.substring(0, Math.min(32, token.length()));
            Set<String> keys = redisTemplate.keys(cacheKey + "*");
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    String cachedToken = redisTemplate.opsForValue().get(key);
                    if (token.equals(cachedToken)) {
                        String keyStr = key;
                        int idx = keyStr.lastIndexOf(":");
                        if (idx > 0) {
                            return keyStr.substring(idx + 1);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Redis token validation failed: {}", e.getMessage());
        }
        return extractUserIdFromToken(token);
    }

    private String extractUserIdFromToken(String token) {
        if (token.contains(".")) {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                try {
                    String timestamp = parts[parts.length - 1];
                    if (timestamp.length() >= 13) {
                        return parts[0];
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
}
