package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 存储对象表实体。
 */
@Data
public class StorageObject {
    private Long id;
    private String objectUuid;
    private String sha256;
    private String md5;
    private Long sizeBytes;
    private String mimeType;
    private String storageProvider;
    private String bucketName;
    private String objectKey;
    private String storageClass;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
