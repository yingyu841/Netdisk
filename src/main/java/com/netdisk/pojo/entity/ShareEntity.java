package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享链接表实体。
 */
@Data
public class ShareEntity {
    private Long id;
    private String shareUuid;
    private Long spaceId;
    private Long creatorUserId;
    private String shareType;
    private String title;
    private String accessCodeHash;
    private Integer allowPreview;
    private Integer allowDownload;
    private Integer maxAccessCount;
    private Integer currentAccessCount;
    private LocalDateTime expiredAt;
    private String status;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
