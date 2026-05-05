package com.netdisk.messaging.chat;

import com.netdisk.config.AppProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class ChatOutboxMonitor {
    private static final Logger log = LoggerFactory.getLogger("biz.chat.mq.outbox.monitor");

    private final ChatOutboxService chatOutboxService;
    private final AppProperties appProperties;
    private final AtomicLong pendingGauge = new AtomicLong(0L);
    private final AtomicLong retryGauge = new AtomicLong(0L);
    private final AtomicLong deadGauge = new AtomicLong(0L);
    private final AtomicLong processingGauge = new AtomicLong(0L);

    public ChatOutboxMonitor(ChatOutboxService chatOutboxService, AppProperties appProperties, MeterRegistry meterRegistry) {
        this.chatOutboxService = chatOutboxService;
        this.appProperties = appProperties;
        meterRegistry.gauge("netdisk.chat.outbox.pending", pendingGauge);
        meterRegistry.gauge("netdisk.chat.outbox.retry", retryGauge);
        meterRegistry.gauge("netdisk.chat.outbox.dead", deadGauge);
        meterRegistry.gauge("netdisk.chat.outbox.processing", processingGauge);
    }

    @Scheduled(fixedDelayString = "${app.chat-mq.outbox-monitor-ms:10000}")
    public void monitor() {
        if (appProperties.getChatMq() == null || !appProperties.getChatMq().isEnabled() || !appProperties.getChatMq().isOutboxEnabled()) {
            return;
        }
        Map<String, Object> stats = chatOutboxService.getOutboxStats();
        long pending = longVal(stats.get("pending"));
        long retry = longVal(stats.get("retry"));
        long dead = longVal(stats.get("dead"));
        long processing = longVal(stats.get("processing"));

        pendingGauge.set(pending);
        retryGauge.set(retry);
        deadGauge.set(dead);
        processingGauge.set(processing);

        if (pending >= Math.max(1, appProperties.getChatMq().getOutboxWarnPendingThreshold())) {
            log.warn("chat outbox pending backlog high: pending={} retry={} dead={} processing={}", pending, retry, dead, processing);
        }
        if (dead >= Math.max(1, appProperties.getChatMq().getOutboxWarnDeadThreshold())) {
            log.error("chat outbox dead events high: dead={} pending={} retry={}", dead, pending, retry);
        }
    }

    private long longVal(Object val) {
        if (val == null) {
            return 0L;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (Exception ex) {
            return 0L;
        }
    }
}
