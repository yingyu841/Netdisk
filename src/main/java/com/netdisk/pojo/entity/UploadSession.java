package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传会话表实体。
 */
@Data
public class UploadSession {
    private Long id;
    private String uploadUuid;
    private Long userId;
    private Long spaceId;
    private Long parentResourceId;
    private String filename;
    private Long totalSize;
    private Integer totalParts;
    private String sha256;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
