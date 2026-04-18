package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资源元数据表实体。
 */
@Data
public class Resource {
    private Long id;
    private String resourceUuid;
    private Long spaceId;
    private Long parentId;
    private String resourceType;
    private String name;
    private String nameNormalized;
    private String extension;
    private Long sizeBytes;
    private Long objectId;
    private String pathCache;
    private Long ownerUserId;
    private Integer isDeleted;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
