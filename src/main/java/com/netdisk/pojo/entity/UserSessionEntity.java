package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户会话表实体。
 */
@Data
public class UserSessionEntity {
    private Long id;
    private String sessionUuid;
    private Long userId;
    private String userUuid;
    private String deviceId;
    private String deviceName;
    private String clientType;
    private String refreshTokenHash;
    private String ip;
    private String userAgent;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
