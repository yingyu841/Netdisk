package com.netdisk.messaging.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netdisk.config.AppProperties;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ChatMessageEventPublisher {
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final Counter publishOkCounter;
    private final Counter publishFailCounter;

    public ChatMessageEventPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            AppProperties appProperties,
            MeterRegistry meterRegistry) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.publishOkCounter = Counter.builder("netdisk.chat.mq.publish.success.total")
                .description("Chat MQ publish success total")
                .register(meterRegistry);
        this.publishFailCounter = Counter.builder("netdisk.chat.mq.publish.fail.total")
                .description("Chat MQ publish fail total")
                .register(meterRegistry);
    }

    public boolean publishMessageSent(ChatMessageSentEvent event) {
        if (event == null) {
            return false;
        }
        if (appProperties.getChatMq() == null || !appProperties.getChatMq().isEnabled()) {
            return false;
        }
        try {
            String body = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(
                    appProperties.getChatMq().getExchange(),
                    appProperties.getChatMq().getRoutingKey(),
                    body,
                    new CorrelationData(UUID.randomUUID().toString())
            );
            publishOkCounter.increment();
            return true;
        } catch (Exception ex) {
            publishFailCounter.increment();
            return false;
        }
    }

    public boolean publishReadBatch(ChatReadBatchEvent event) {
        if (event == null) {
            return false;
        }
        if (appProperties.getChatMq() == null || !appProperties.getChatMq().isEnabled()) {
            return false;
        }
        try {
            String body = objectMapper.writeValueAsString(event);
            rabbitTemplate.convertAndSend(
                    appProperties.getChatMq().getExchange(),
                    appProperties.getChatMq().getReadBatchRoutingKey(),
                    body,
                    new CorrelationData(UUID.randomUUID().toString())
            );
            publishOkCounter.increment();
            return true;
        } catch (Exception ex) {
            publishFailCounter.increment();
            return false;
        }
    }

    public boolean publishRetry(String originalRoutingKey, String body, int retryCount) {
        try {
            rabbitTemplate.convertAndSend(
                    appProperties.getChatMq().getRetryExchange(),
                    originalRoutingKey,
                    body,
                    message -> {
                        message.getMessageProperties().setHeader("x-retry-count", Integer.valueOf(retryCount));
                        message.getMessageProperties().setHeader("x-original-routing-key", originalRoutingKey);
                        message.getMessageProperties().setContentType("application/json");
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    },
                    new CorrelationData(UUID.randomUUID().toString())
            );
            publishOkCounter.increment();
            return true;
        } catch (Exception ex) {
            publishFailCounter.increment();
            return false;
        }
    }

    public boolean publishDead(String originalRoutingKey, String body, int retryCount, String reason) {
        try {
            rabbitTemplate.convertAndSend(
                    appProperties.getChatMq().getDeadExchange(),
                    appProperties.getChatMq().getDeadRoutingKey(),
                    body,
                    message -> {
                        message.getMessageProperties().setHeader("x-retry-count", Integer.valueOf(retryCount));
                        message.getMessageProperties().setHeader("x-original-routing-key", originalRoutingKey);
                        message.getMessageProperties().setHeader("x-dead-reason", reason == null ? "" : reason);
                        message.getMessageProperties().setContentType("application/json");
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    },
                    new CorrelationData(UUID.randomUUID().toString())
            );
            publishOkCounter.increment();
            return true;
        } catch (Exception ex) {
            publishFailCounter.increment();
            return false;
        }
    }
}
