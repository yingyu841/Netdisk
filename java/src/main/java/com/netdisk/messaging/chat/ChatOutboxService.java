package com.netdisk.messaging.chat;

import java.util.Map;

public interface ChatOutboxService {
    void enqueueMessageSent(ChatMessageSentEvent event);

    void enqueueReadBatch(ChatReadBatchEvent event);

    int dispatchPendingEvents();

    Map<String, Object> getOutboxStats();

    Map<String, Object> listDeadEvents(Integer page, Integer pageSize);

    boolean replayDeadEvent(Long outboxId);

    int cleanupSentEvents(int retentionDays);
}
