package com.netdisk.config;

import com.netdisk.websocket.ChatWebSocketHandler;
import com.netdisk.websocket.ChatWebSocketInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ObjectProvider<ChatWebSocketHandler> chatWebSocketHandler;
    private final ObjectProvider<ChatWebSocketInterceptor> chatWebSocketInterceptor;

    public WebSocketConfig(
            ObjectProvider<ChatWebSocketHandler> chatWebSocketHandler,
            ObjectProvider<ChatWebSocketInterceptor> chatWebSocketInterceptor) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.chatWebSocketInterceptor = chatWebSocketInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        ChatWebSocketHandler handler = chatWebSocketHandler.getIfAvailable();
        ChatWebSocketInterceptor interceptor = chatWebSocketInterceptor.getIfAvailable();

        if (handler != null) {
            registry.addHandler(handler, "/ws/chat")
                    .addInterceptors(interceptor != null ? interceptor : new ChatWebSocketInterceptor())
                    .setAllowedOrigins("*");
        }
    }
}
