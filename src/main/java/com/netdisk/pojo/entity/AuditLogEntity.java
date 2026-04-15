package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志表实体。
 */
@Data
public class AuditLogEntity {
    private Long id;
    private String eventUuid;
    private Long actorUserId;
    private String actorType;
    private String eventType;
    private String targetType;
    private String targetId;
    private String result;
    private String reason;
    private String metadataJson;
    private LocalDateTime createdAt;
}
