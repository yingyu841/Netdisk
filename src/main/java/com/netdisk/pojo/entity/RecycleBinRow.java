package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回收站列表查询行（含资源与回收站字段）。
 */
@Data
public class RecycleBinRow {
    private String resourceUuid;
    private String name;
    private String resourceType;
    private String extension;
    private Long sizeBytes;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private LocalDateTime purgeAt;
    private String originalParentUuid;
}
