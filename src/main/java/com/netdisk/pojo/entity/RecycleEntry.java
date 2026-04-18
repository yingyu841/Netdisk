package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站表实体。
 */
@Data
public class RecycleEntry {
    private Long id;
    private Long resourceId;
    private Long spaceId;
    private Long deletedByUserId;
    private Long originalParentId;
    private LocalDateTime deletedAt;
    private LocalDateTime purgeAt;
    private LocalDateTime restoredAt;
    private LocalDateTime createdAt;
}
