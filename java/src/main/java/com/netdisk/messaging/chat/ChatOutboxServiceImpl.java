package com.netdisk.messaging.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netdisk.config.AppProperties;
import com.netdisk.service.ChatMessageAsyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ChatOutboxServiceImpl implements ChatOutboxService {
    private static final Logger log = LoggerFactory.getLogger("biz.chat.mq.outbox");
    private static final String TYPE_MESSAGE_SENT = "chat_message_sent";
    private static final String TYPE_READ_BATCH = "chat_read_batch";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_RETRY = "retry";
    private static final String STATUS_PROCESSING = "processing";
    private static final String STATUS_SENT = "sent";
    private static final String STATUS_DEAD = "dead";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final ChatMessageEventPublisher chatMessageEventPublisher;
    private final ChatMessageAsyncService chatMessageAsyncService;

    public ChatOutboxServiceImpl(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            AppProperties appProperties,
            ChatMessageEventPublisher chatMessageEventPublisher,
            ChatMessageAsyncService chatMessageAsyncService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.appProperties = appProperties;
        this.chatMessageEventPublisher = chatMessageEventPublisher;
        this.chatMessageAsyncService = chatMessageAsyncService;
    }

    @Override
    @Transactional
    public void enqueueMessageSent(ChatMessageSentEvent event) {
        if (event != null && safe(event.getEventId()).isEmpty()) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (!isOutboxEnabled()) {
            boolean ok = chatMessageEventPublisher.publishMessageSent(event);
            if (!ok) {
                chatMessageAsyncService.processMessageSent(event);
            }
            return;
        }
        enqueue(TYPE_MESSAGE_SENT, safe(event == null ? null : event.getConversationUuid()), event == null ? null : event.getEventId(), toJson(event));
    }

    @Override
    @Transactional
    public void enqueueReadBatch(ChatReadBatchEvent event) {
        if (event != null && safe(event.getEventId()).isEmpty()) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (!isOutboxEnabled()) {
            boolean ok = chatMessageEventPublisher.publishReadBatch(event);
            if (!ok) {
                chatMessageAsyncService.processReadBatch(event);
            }
            return;
        }
        enqueue(TYPE_READ_BATCH, safe(event == null ? null : event.getConversationUuid()), event == null ? null : event.getEventId(), toJson(event));
    }

    @Override
    @Transactional
    public int dispatchPendingEvents() {
        if (appProperties.getChatMq() == null || !appProperties.getChatMq().isEnabled() || !appProperties.getChatMq().isOutboxEnabled()) {
            return 0;
        }
        int batchSize = Math.max(1, appProperties.getChatMq().getOutboxBatchSize());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, event_type, payload_json, retry_count, max_retry " +
                        "FROM chat_outbox_events " +
                        "WHERE status IN (?, ?) AND next_retry_at <= NOW() " +
                        "ORDER BY id ASC LIMIT ?",
                STATUS_PENDING, STATUS_RETRY, batchSize
        );
        int processed = 0;
        for (Map<String, Object> row : rows) {
            Long id = longVal(row.get("id"));
            if (id == null) {
                continue;
            }
            int lock = jdbcTemplate.update(
                    "UPDATE chat_outbox_events SET status = ?, updated_at = NOW() WHERE id = ? AND status IN (?, ?)",
                    STATUS_PROCESSING, id, STATUS_PENDING, STATUS_RETRY
            );
            if (lock <= 0) {
                continue;
            }
            processOne(id, safe(str(row.get("event_type"))), safe(str(row.get("payload_json"))), intVal(row.get("retry_count")), intVal(row.get("max_retry")));
            processed++;
        }
        if (processed > 0) {
            log.info("chat outbox dispatched events={}", processed);
        }
        return processed;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getOutboxStats() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("pending", queryCountByStatus(STATUS_PENDING));
        data.put("retry", queryCountByStatus(STATUS_RETRY));
        data.put("processing", queryCountByStatus(STATUS_PROCESSING));
        data.put("dead", queryCountByStatus(STATUS_DEAD));
        data.put("sent", queryCountByStatus(STATUS_SENT));
        return data;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> listDeadEvents(Integer page, Integer pageSize) {
        int p = page == null || page.intValue() < 1 ? 1 : page.intValue();
        int size = pageSize == null || pageSize.intValue() < 1 ? 20 : Math.min(200, pageSize.intValue());
        int offset = (p - 1) * size;
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM chat_outbox_events WHERE status = ?",
                Long.class,
                STATUS_DEAD
        );
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT id, event_uuid, event_type, aggregate_id, retry_count, max_retry, next_retry_at, last_error, created_at, updated_at, payload_json " +
                        "FROM chat_outbox_events WHERE status = ? ORDER BY id DESC LIMIT ?, ?",
                STATUS_DEAD, offset, size
        );
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("total", total == null ? 0L : total.longValue());
        data.put("items", items);
        return data;
    }

    @Override
    @Transactional
    public boolean replayDeadEvent(Long outboxId) {
        if (outboxId == null || outboxId.longValue() <= 0L) {
            return false;
        }
        int updated = jdbcTemplate.update(
                "UPDATE chat_outbox_events " +
                        "SET status = ?, retry_count = 0, next_retry_at = NOW(), last_error = NULL, updated_at = NOW() " +
                        "WHERE id = ? AND status = ?",
                STATUS_RETRY, outboxId, STATUS_DEAD
        );
        if (updated > 0) {
            log.warn("chat outbox replay requested outboxId={}", outboxId);
        }
        return updated > 0;
    }

    @Override
    @Transactional
    public int cleanupSentEvents(int retentionDays) {
        int days = retentionDays <= 0 ? 7 : retentionDays;
        int deleted = jdbcTemplate.update(
                "DELETE FROM chat_outbox_events WHERE status = ? AND sent_at IS NOT NULL AND sent_at < DATE_SUB(NOW(), INTERVAL ? DAY)",
                STATUS_SENT, days
        );
        if (deleted > 0) {
            log.info("chat outbox cleanup deleted={} retentionDays={}", deleted, days);
        }
        return deleted;
    }

    private void processOne(Long id, String eventType, String payloadJson, int retryCount, int maxRetry) {
        boolean ok = false;
        if (TYPE_MESSAGE_SENT.equals(eventType)) {
            ChatMessageSentEvent event = fromJson(payloadJson, ChatMessageSentEvent.class);
            ok = chatMessageEventPublisher.publishMessageSent(event);
        } else if (TYPE_READ_BATCH.equals(eventType)) {
            ChatReadBatchEvent event = fromJson(payloadJson, ChatReadBatchEvent.class);
            ok = chatMessageEventPublisher.publishReadBatch(event);
        }
        if (ok) {
            jdbcTemplate.update(
                    "UPDATE chat_outbox_events SET status = ?, sent_at = NOW(), updated_at = NOW() WHERE id = ?",
                    STATUS_SENT, id
            );
            return;
        }

        int nextRetry = retryCount + 1;
        if (nextRetry >= Math.max(1, maxRetry)) {
            jdbcTemplate.update(
                    "UPDATE chat_outbox_events SET status = ?, retry_count = ?, last_error = ?, updated_at = NOW() WHERE id = ?",
                    STATUS_DEAD, nextRetry, "publish_failed", id
            );
            log.error("chat outbox event moved to dead id={} eventType={} retry={}", id, eventType, nextRetry);
            return;
        }
        int retryDelay = Math.max(1, appProperties.getChatMq().getOutboxRetryDelaySeconds());
        jdbcTemplate.update(
                "UPDATE chat_outbox_events SET status = ?, retry_count = ?, next_retry_at = DATE_ADD(NOW(), INTERVAL ? SECOND), last_error = ?, updated_at = NOW() WHERE id = ?",
                STATUS_RETRY, nextRetry, retryDelay, "publish_failed", id
        );
        log.warn("chat outbox event retry scheduled id={} eventType={} retry={} delaySeconds={}", id, eventType, nextRetry, retryDelay);
    }

    private long queryCountByStatus(String status) {
        Long val = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM chat_outbox_events WHERE status = ?",
                Long.class,
                status
        );
        return val == null ? 0L : val.longValue();
    }

    private void enqueue(String eventType, String aggregateId, String eventUuid, String payloadJson) {
        String finalEventUuid = safe(eventUuid).isEmpty() ? UUID.randomUUID().toString() : safe(eventUuid);
        jdbcTemplate.update(
                "INSERT INTO chat_outbox_events (event_uuid, event_type, aggregate_id, payload_json, status, retry_count, max_retry, next_retry_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, CAST(? AS JSON), ?, 0, ?, NOW(), NOW(), NOW())",
                finalEventUuid,
                eventType,
                aggregateId,
                payloadJson,
                STATUS_PENDING,
                Math.max(1, appProperties.getChatMq().getMaxRetry())
        );
    }

    private boolean isOutboxEnabled() {
        return appProperties.getChatMq() != null && appProperties.getChatMq().isEnabled() && appProperties.getChatMq().isOutboxEnabled();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? new Object() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, clazz);
        } catch (Exception ex) {
            try {
                return clazz.newInstance();
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private String str(Object val) {
        return val == null ? "" : String.valueOf(val);
    }

    private String safe(String text) {
        return text == null ? "" : text.trim();
    }

    private Long longVal(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return Long.valueOf(((Number) val).longValue());
        }
        String text = safe(str(val));
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private int intVal(Object val) {
        if (val == null) {
            return 0;
        }
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        String text = safe(str(val));
        if (text.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (Exception ex) {
            return 0;
        }
    }
}
