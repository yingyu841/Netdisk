package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 空间成员表实体。
 */
@Data
public class SpaceMemberEntity {
    private Long id;
    private Long spaceId;
    private Long userId;
    private String roleCode;
    private Integer status;
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
