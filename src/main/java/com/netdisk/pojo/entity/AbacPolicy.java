package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * ABAC 策略表实体。
 */
@Data
public class AbacPolicy {
    private Long id;
    private String policyUuid;
    private String policyScope;
    private Long scopeId;
    private String conditionJson;
    private String effect;
    private Integer priority;
    private String status;
    private Long createdByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
