package com.netdisk.controller;

import com.netdisk.common.web.ApiResponse;
import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.messaging.chat.ChatOutboxService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/chat/outbox")
public class ChatOutboxController {
    private final ChatOutboxService chatOutboxService;

    public ChatOutboxController(ChatOutboxService chatOutboxService) {
        this.chatOutboxService = chatOutboxService;
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(HttpServletRequest req) {
        return ApiResponse.ok(chatOutboxService.getOutboxStats(), requestId(req));
    }

    @GetMapping("/dead")
    public ApiResponse<Map<String, Object>> deadEvents(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            HttpServletRequest req) {
        return ApiResponse.ok(chatOutboxService.listDeadEvents(page, pageSize), requestId(req));
    }

    @PostMapping("/dead/{outboxId}/replay")
    public ApiResponse<Map<String, Object>> replayDead(
            @PathVariable Long outboxId,
            HttpServletRequest req) {
        boolean ok = chatOutboxService.replayDeadEvent(outboxId);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("outboxId", outboxId);
        data.put("replayed", ok);
        return ApiResponse.ok(data, requestId(req));
    }

    @PostMapping("/dispatch")
    public ApiResponse<Map<String, Object>> dispatchNow(HttpServletRequest req) {
        int processed = chatOutboxService.dispatchPendingEvents();
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("processed", processed);
        return ApiResponse.ok(data, requestId(req));
    }

    @PostMapping("/cleanup")
    public ApiResponse<Map<String, Object>> cleanup(
            @RequestParam(required = false, defaultValue = "7") Integer retentionDays,
            HttpServletRequest req) {
        int deleted = chatOutboxService.cleanupSentEvents(retentionDays == null ? 7 : retentionDays.intValue());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("retentionDays", retentionDays == null ? 7 : retentionDays.intValue());
        data.put("deleted", deleted);
        return ApiResponse.ok(data, requestId(req));
    }

    private String requestId(HttpServletRequest request) {
        Object val = request.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
        return val == null ? "" : String.valueOf(val);
    }
}
