package com.netdisk.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netdisk.common.exception.BizException;
import com.netdisk.common.web.RequestIdFilter;
import com.netdisk.mapper.UserMapper;
import com.netdisk.pojo.entity.AuditLog;
import com.netdisk.pojo.entity.User;
import com.netdisk.service.AuditLogRecorder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 拦截 {@link com.netdisk.controller.ResourceController} 的对外方法，将资源类操作写入 audit_logs。
 */
@Aspect
@Component
public class ResourceOperationAuditAspect {
    private static final String TARGET_TYPE_RESOURCE = "resource";
    private static final String ACTOR_TYPE_USER = "user";
    private static final int TARGET_ID_MAX = 64;
    private static final int REASON_MAX = 255;

    private final AuditLogRecorder auditLogRecorder;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public ResourceOperationAuditAspect(AuditLogRecorder auditLogRecorder, UserMapper userMapper, ObjectMapper objectMapper) {
        this.auditLogRecorder = auditLogRecorder;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Around("execution(public * com.netdisk.controller.ResourceController.*(..))")
    public Object aroundResourceController(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        HttpServletRequest req = currentRequest();
        String methodName = pjp.getSignature() instanceof MethodSignature
                ? ((MethodSignature) pjp.getSignature()).getMethod().getName()
                : "unknown";
        String eventType = mapEventType(methodName);
        String targetId = extractTargetId(req);
        Long actorUserId = resolveActorUserId(req);
        Throwable error = null;
        Object result = null;
        try {
            result = pjp.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            if (eventType != null) {
                long duration = System.currentTimeMillis() - start;
                boolean success = error == null;
                String resultCode = success ? "success" : "fail";
                String reason = success ? null : truncateReason(error);
                String metadata = buildMetadata(req, duration, methodName, success);
                AuditLog row = newRow(actorUserId, eventType, targetId, resultCode, reason, metadata);
                auditLogRecorder.record(row);
            }
        }
        return result;
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    private Long resolveActorUserId(HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        Object raw = req.getAttribute("authUserId");
        if (raw == null) {
            return null;
        }
        String userUuid = String.valueOf(raw).trim();
        if (userUuid.isEmpty() || "null".equalsIgnoreCase(userUuid)) {
            return null;
        }
        User user = userMapper.findByUserUuid(userUuid);
        return user == null ? null : user.getId();
    }

    private AuditLog newRow(Long actorUserId, String eventType, String targetId, String result, String reason, String metadataJson) {
        AuditLog log = new AuditLog();
        log.setEventUuid(UUID.randomUUID().toString());
        log.setActorUserId(actorUserId);
        log.setActorType(ACTOR_TYPE_USER);
        log.setEventType(eventType);
        log.setTargetType(TARGET_TYPE_RESOURCE);
        log.setTargetId(truncateTargetId(targetId));
        log.setResult(result);
        log.setReason(reason);
        log.setMetadataJson(metadataJson);
        return log;
    }

    private static String mapEventType(String methodName) {
        if (methodName == null) {
            return null;
        }
        switch (methodName) {
            case "purgeRecycle":
                return "resource_recycle_purge";
            case "createFolder":
                return "resource_folder_create";
            case "move":
                return "resource_move";
            case "copy":
                return "resource_copy";
            case "rename":
                return "resource_rename";
            case "delete":
                return "resource_soft_delete";
            case "restore":
                return "resource_restore";
            default:
                return null;
        }
    }

    /**
     * 从 /api/v1/resources/{uuid}/... 解析资源业务 ID；目录级操作返回 "-"。
     */
    private static String extractTargetId(HttpServletRequest req) {
        if (req == null) {
            return "-";
        }
        String uri = req.getRequestURI();
        String ctx = req.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        int idx = uri.indexOf("/resources/");
        if (idx < 0) {
            return "-";
        }
        String tail = uri.substring(idx + "/resources/".length());
        if (tail.isEmpty()) {
            return "-";
        }
        String first = tail.split("/")[0];
        if ("recycle".equals(first) || "tree".equals(first) || "folders".equals(first)
                || "move".equals(first) || "copy".equals(first)) {
            return "-";
        }
        if (first.length() == 36 && first.charAt(8) == '-') {
            return first;
        }
        return "-";
    }

    private String buildMetadata(HttpServletRequest req, long durationMs, String controllerMethod, boolean success) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("controllerMethod", controllerMethod);
        m.put("durationMs", durationMs);
        m.put("success", success);
        if (req != null) {
            m.put("httpMethod", req.getMethod());
            m.put("path", req.getRequestURI());
            Object rid = req.getAttribute(RequestIdFilter.REQUEST_ID_KEY);
            if (rid != null) {
                m.put("requestId", String.valueOf(rid));
            }
        }
        try {
            return objectMapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String truncateTargetId(String id) {
        if (id == null || id.isEmpty()) {
            return "-";
        }
        return id.length() <= TARGET_ID_MAX ? id : id.substring(0, TARGET_ID_MAX);
    }

    private static String truncateReason(Throwable error) {
        if (error == null) {
            return null;
        }
        String msg;
        if (error instanceof BizException) {
            msg = error.getMessage();
        } else {
            msg = error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
        }
        if (msg == null) {
            return null;
        }
        String t = msg.trim();
        return t.length() <= REASON_MAX ? t : t.substring(0, REASON_MAX);
    }
}
