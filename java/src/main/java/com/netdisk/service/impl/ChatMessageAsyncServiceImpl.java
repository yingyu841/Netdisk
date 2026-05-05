package com.netdisk.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netdisk.messaging.chat.ChatReadBatchEvent;
import com.netdisk.messaging.chat.ChatMessageSentEvent;
import com.netdisk.service.ChatMessageAsyncService;
import com.netdisk.service.chat.ChatCacheIndexService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ChatMessageAsyncServiceImpl implements ChatMessageAsyncService {
    private static final long UNREAD_COUNTER_CACHE_SECONDS = 86400L;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final ChatCacheIndexService chatCacheIndexService;

    public ChatMessageAsyncServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            ObjectProvider<StringRedisTemplate> redisProvider,
            ChatCacheIndexService chatCacheIndexService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisProvider.getIfAvailable();
        this.chatCacheIndexService = chatCacheIndexService;
    }

    @Override
    @Transactional
    public void processMessageSent(ChatMessageSentEvent event) {
        if (event == null || event.getConversationId() == null || event.getMessageId() == null || event.getSenderUserId() == null) {
            return;
        }
        if (!tryAcquireEventConsume(safe(event.getEventId()), "chat_message_sent")) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE chat_conversations SET last_message_id = ?, last_message_at = NOW(), updated_at = NOW() WHERE id = ?",
                event.getMessageId(), event.getConversationId()
        );
        logConversationEvent(event);
        incrementUnreadCounterForConversation(event.getConversationId(), safe(event.getConversationUuid()), event.getSenderUserId());
        evictConversationRelatedCache(event.getConversationId(), safe(event.getConversationUuid()));
    }

    @Override
    @Transactional
    public int processReadBatch(ChatReadBatchEvent event) {
        if (event == null || event.getConversationId() == null || event.getUserId() == null) {
            return 0;
        }
        if (!tryAcquireEventConsume(safe(event.getEventId()), "chat_read_batch")) {
            return 0;
        }
        List<String> messageIds = event.getMessageIds();
        if (messageIds == null || messageIds.isEmpty()) {
            return 0;
        }
        int affected = 0;
        for (String messageId : messageIds) {
            Long msgPk = resolveMessageId(messageId);
            if (msgPk == null) {
                continue;
            }
            Map<String, Object> msg = requireMessage(msgPk);
            Long convId = asLong(msg.get("conversation_id"));
            if (convId == null || convId.longValue() != event.getConversationId().longValue()) {
                continue;
            }
            markReadInternal(event.getConversationId(), event.getUserId(), msgPk);
            affected++;
        }
        refreshUnreadCounter(event.getConversationId(), safe(event.getConversationUuid()), event.getUserId());
        evictConversationRelatedCache(event.getConversationId(), safe(event.getConversationUuid()));
        return affected;
    }

    @Override
    @Transactional
    public int cleanupConsumerEventLog(int retentionDays) {
        int days = retentionDays <= 0 ? 7 : retentionDays;
        return jdbcTemplate.update(
                "DELETE FROM chat_consumer_event_log WHERE created_at < DATE_SUB(NOW(), INTERVAL ? DAY)",
                days
        );
    }

    private void logConversationEvent(ChatMessageSentEvent event) {
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("messageId", safe(event.getMessageUuid()));
            payload.put("messageType", safe(event.getMessageType()));
            String payloadJson = objectMapper.writeValueAsString(payload);
            jdbcTemplate.update(
                    "INSERT INTO chat_conversation_events (event_uuid, conversation_id, operator_user_id, event_type, event_payload_json, created_at) " +
                            "VALUES (?, ?, ?, ?, CAST(? AS JSON), NOW())",
                    UUID.randomUUID().toString(),
                    event.getConversationId(),
                    event.getSenderUserId(),
                    "message_send",
                    payloadJson
            );
        } catch (JsonProcessingException ignored) {
        }
    }

    private void incrementUnreadCounterForConversation(Long conversationId, String conversationUuid, Long senderUserId) {
        if (redisTemplate == null || conversationId == null || conversationUuid.isEmpty()) {
            return;
        }
        try {
            List<Long> memberUserIds = jdbcTemplate.query(
                    "SELECT user_id FROM chat_conversation_members WHERE conversation_id = ? AND status = 'active'",
                    (rs, rowNum) -> rs.getLong(1),
                    conversationId
            );
            for (Long uid : memberUserIds) {
                if (uid == null) {
                    continue;
                }
                String key = unreadCounterKey(uid, conversationUuid);
                if (senderUserId != null && uid.longValue() == senderUserId.longValue()) {
                    redisTemplate.opsForValue().set(key, "0", UNREAD_COUNTER_CACHE_SECONDS, TimeUnit.SECONDS);
                    continue;
                }
                redisTemplate.opsForValue().increment(key, 1L);
                redisTemplate.expire(key, UNREAD_COUNTER_CACHE_SECONDS, TimeUnit.SECONDS);
            }
        } catch (Exception ignore) {
        }
    }

    private void evictConversationRelatedCache(Long conversationId, String conversationUuid) {
        if (redisTemplate == null || conversationId == null) {
            return;
        }
        try {
            List<Long> memberUserIds = jdbcTemplate.query(
                    "SELECT user_id FROM chat_conversation_members WHERE conversation_id = ? AND status = 'active'",
                    (rs, rowNum) -> rs.getLong(1),
                    conversationId
            );
            for (Long uid : memberUserIds) {
                if (uid == null) {
                    continue;
                }
                chatCacheIndexService.evictByIndexes(Arrays.asList(
                        chatCacheIndexService.userListIndexKey(uid),
                        chatCacheIndexService.conversationUserIndexKey(uid, conversationUuid)
                ));
            }
        } catch (Exception ignore) {
        }
    }

    private Long resolveMessageId(String messageIdOrUuid) {
        String value = safe(messageIdOrUuid);
        if (value.isEmpty()) {
            return null;
        }
        if (isDigits(value)) {
            return Long.valueOf(value);
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM chat_messages WHERE message_uuid = ?",
                (rs, rowNum) -> rs.getLong(1),
                value
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Map<String, Object> requireMessage(Long messageId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, conversation_id FROM chat_messages WHERE id = ?",
                messageId
        );
        return rows.isEmpty() ? new LinkedHashMap<String, Object>() : rows.get(0);
    }

    private void markReadInternal(Long convId, Long userId, Long msgId) {
        jdbcTemplate.update(
                "INSERT INTO chat_message_receipts (message_id, user_id, receipt_type, receipt_at, created_at) VALUES (?, ?, 'read', NOW(), NOW()) " +
                        "ON DUPLICATE KEY UPDATE receipt_at = VALUES(receipt_at)",
                msgId, userId
        );
        jdbcTemplate.update(
                "UPDATE chat_conversation_members SET last_read_message_id = ?, last_read_at = NOW(), updated_at = NOW() " +
                        "WHERE conversation_id = ? AND user_id = ?",
                msgId, convId, userId
        );
    }

    private void refreshUnreadCounter(Long conversationId, String conversationUuid, Long userId) {
        if (redisTemplate == null || conversationId == null || userId == null || conversationUuid.isEmpty()) {
            return;
        }
        Long unread = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) " +
                        "FROM chat_messages m " +
                        "WHERE m.conversation_id = ? " +
                        "AND m.id > IFNULL((SELECT cm.last_read_message_id FROM chat_conversation_members cm WHERE cm.conversation_id = ? AND cm.user_id = ?), 0)",
                Long.class,
                conversationId, conversationId, userId
        );
        long safeUnread = unread == null ? 0L : Math.max(0L, unread.longValue());
        redisTemplate.opsForValue().set(unreadCounterKey(userId, conversationUuid), String.valueOf(safeUnread), UNREAD_COUNTER_CACHE_SECONDS, TimeUnit.SECONDS);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        String text = safe(String.valueOf(value));
        if (!isDigits(text)) {
            return null;
        }
        return Long.valueOf(text);
    }

    private boolean isDigits(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String unreadCounterKey(Long userId, String conversationId) {
        return "chat:conv:unread:counter:user:" + userId + ":conv:" + conversationId;
    }

    private String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private boolean tryAcquireEventConsume(String eventId, String eventType) {
        String eid = safe(eventId);
        if (eid.isEmpty()) {
            // 无eventId时保持兼容：按原逻辑处理，不做去重
            return true;
        }
        int affected = jdbcTemplate.update(
                "INSERT INTO chat_consumer_event_log (event_id, event_type, status, created_at) VALUES (?, ?, ?, NOW()) " +
                        "ON DUPLICATE KEY UPDATE event_id = event_id",
                eid, safe(eventType), "consumed"
        );
        return affected > 0;
    }
}
