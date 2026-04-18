package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享访问日志表实体。
 */
@Data
public class ShareAccessLog {
    private Long id;
    private Long shareId;
    private Long visitorUserId;
    private String accessStatus;
    private String actionType;
    private String ip;
    private String userAgent;
    private LocalDateTime accessedAt;
}
