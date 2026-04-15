package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 分享资源关系表实体。
 */
@Data
public class ShareResourceEntity {
    private Long id;
    private Long shareId;
    private Long resourceId;
    private LocalDateTime createdAt;
}
