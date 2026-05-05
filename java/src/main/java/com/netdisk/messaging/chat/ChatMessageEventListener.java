package com.netdisk.messaging.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netdisk.config.AppProperties;
import com.netdisk.service.ChatMessageAsyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import com.netdisk.common.exception.BizException;

@Component
@ConditionalOnProperty(prefix = "app.chat-mq", name = "enabled", havingValue = "true")
public class ChatMessageEventListener {
    private static final Logger log = LoggerFactory.getLogger("biz.chat.mq.listener");
    private final ObjectMapper objectMapper;
    private final ChatMessageAsyncService chatMessageAsyncService;
    private final ChatMessageEventPublisher chatMessageEventPublisher;
    private final AppProperties appProperties;

    public ChatMessageEventListener(
            ObjectMapper objectMapper,
            ChatMessageAsyncService chatMessageAsyncService,
            ChatMessageEventPublisher chatMessageEventPublisher,
            AppProperties appProperties) {
        this.objectMapper = objectMapper;
        this.chatMessageAsyncService = chatMessageAsyncService;
        this.chatMessageEventPublisher = chatMessageEventPublisher;
        this.appProperties = appProperties;
    }

    @RabbitListener(queues = "${app.chat-mq.queue:netdisk.chat.message.sent.queue}")
    public void onMessageSent(
            String body,
            @Header(value = "x-retry-count", required = false) Integer retryCount,
            @Header(value = "x-original-routing-key", required = false) String originalRoutingKey) {
        if (body == null || body.trim().isEmpty()) {
            return;
        }
        try {
            ChatMessageSentEvent event = objectMapper.readValue(body, ChatMessageSentEvent.class);
            chatMessageAsyncService.processMessageSent(event);
        } catch (Exception ex) {
            handleFailure(
                    body,
                    valueOrDefault(originalRoutingKey, appProperties.getChatMq().getRoutingKey()),
                    retryCount,
                    ex
            );
        }
    }

    @RabbitListener(queues = "${app.chat-mq.read-batch-queue:netdisk.chat.read.batch.queue}")
    public void onReadBatch(
            String body,
            @Header(value = "x-retry-count", required = false) Integer retryCount,
            @Header(value = "x-original-routing-key", required = false) String originalRoutingKey) {
        if (body == null || body.trim().isEmpty()) {
            return;
        }
        try {
            ChatReadBatchEvent event = objectMapper.readValue(body, ChatReadBatchEvent.class);
            chatMessageAsyncService.processReadBatch(event);
        } catch (Exception ex) {
            handleFailure(
                    body,
                    valueOrDefault(originalRoutingKey, appProperties.getChatMq().getReadBatchRoutingKey()),
                    retryCount,
                    ex
            );
        }
    }

    private void handleFailure(String body, String routingKey, Integer retryCount, Exception ex) {
        int current = retryCount == null ? 0 : Math.max(0, retryCount.intValue());
        int maxRetry = Math.max(0, appProperties.getChatMq().getMaxRetry());
        if (!isRetryable(ex)) {
            log.error("chat mq non-retryable consume error routingKey={} reason={}",
                    routingKey, ex == null ? "unknown" : ex.getClass().getSimpleName());
            boolean deadPublished = chatMessageEventPublisher.publishDead(routingKey, body, current, ex == null ? "unknown" : ex.getClass().getSimpleName());
            if (!deadPublished) {
                log.error("chat mq dead publish failed for non-retryable error routingKey={} retry={}", routingKey, current);
            }
            return;
        }
        if (current < maxRetry) {
            log.warn("chat mq consume failed, send to retry routingKey={} retry={}/{} reason={}",
                    routingKey, current + 1, maxRetry, ex == null ? "unknown" : ex.getClass().getSimpleName());
            boolean retryPublished = chatMessageEventPublisher.publishRetry(routingKey, body, current + 1);
            if (!retryPublished) {
                log.error("chat mq retry publish failed routingKey={} retry={}", routingKey, current + 1);
            }
            return;
        }
        log.error("chat mq consume failed, send to dead routingKey={} retry={} reason={}",
                routingKey, current, ex == null ? "unknown" : ex.getClass().getSimpleName());
        boolean deadPublished = chatMessageEventPublisher.publishDead(routingKey, body, current, ex == null ? "unknown" : ex.getClass().getSimpleName());
        if (!deadPublished) {
            log.error("chat mq dead publish failed routingKey={} retry={}", routingKey, current);
        }
    }

    private boolean isRetryable(Exception ex) {
        if (ex == null) {
            return true;
        }
        if (ex instanceof com.fasterxml.jackson.core.JsonProcessingException) {
            return false;
        }
        if (ex instanceof IllegalArgumentException) {
            return false;
        }
        if (ex instanceof BizException) {
            return false;
        }
        return true;
    }

    private String valueOrDefault(String value, String fallback) {
        String v = value == null ? "" : value.trim();
        return v.isEmpty() ? fallback : v;
    }
}
