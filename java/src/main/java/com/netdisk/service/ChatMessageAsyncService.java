package com.netdisk.service;

import com.netdisk.messaging.chat.ChatMessageSentEvent;
import com.netdisk.messaging.chat.ChatReadBatchEvent;

public interface ChatMessageAsyncService {
    void processMessageSent(ChatMessageSentEvent event);

    int processReadBatch(ChatReadBatchEvent event);

    int cleanupConsumerEventLog(int retentionDays);
}
