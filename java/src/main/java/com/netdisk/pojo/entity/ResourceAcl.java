package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资源 ACL 表实体。
 */
@Data
public class ResourceAcl {
    private Long id;
    private Long resourceId;
    private String subjectType;
    private String subjectId;
    private Integer canRead;
    private Integer canWrite;
    private Integer canDelete;
    private Integer canShare;
    private Integer canDownload;
    private Integer canManageAcl;
    private Integer inheritFlag;
    private Long createdByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
