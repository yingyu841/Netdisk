package com.netdisk.messaging.chat;

import com.netdisk.config.AppProperties;
import com.netdisk.service.ChatMessageAsyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChatOutboxDispatcher {
    private final ChatOutboxService chatOutboxService;
    private final ChatMessageAsyncService chatMessageAsyncService;
    private final AppProperties appProperties;

    public ChatOutboxDispatcher(
            ChatOutboxService chatOutboxService,
            ChatMessageAsyncService chatMessageAsyncService,
            AppProperties appProperties) {
        this.chatOutboxService = chatOutboxService;
        this.chatMessageAsyncService = chatMessageAsyncService;
        this.appProperties = appProperties;
    }

    @Scheduled(fixedDelayString = "${app.chat-mq.outbox-poll-ms:1000}")
    public void dispatch() {
        if (appProperties.getChatMq() == null || !appProperties.getChatMq().isEnabled() || !appProperties.getChatMq().isOutboxEnabled()) {
            return;
        }
        chatOutboxService.dispatchPendingEvents();
    }

    @Scheduled(fixedDelayString = "${app.chat-mq.outbox-cleanup-ms:3600000}")
    public void cleanupSent() {
        if (appProperties.getChatMq() == null || !appProperties.getChatMq().isEnabled() || !appProperties.getChatMq().isOutboxEnabled()) {
            return;
        }
        int days = Math.max(1, appProperties.getChatMq().getOutboxSentRetentionDays());
        chatOutboxService.cleanupSentEvents(days);
    }

    @Scheduled(fixedDelayString = "${app.chat-mq.consumer-log-cleanup-ms:3600000}")
    public void cleanupConsumerLog() {
        if (appProperties.getChatMq() == null || !appProperties.getChatMq().isEnabled()) {
            return;
        }
        int days = Math.max(1, appProperties.getChatMq().getConsumerLogRetentionDays());
        chatMessageAsyncService.cleanupConsumerEventLog(days);
    }
}
