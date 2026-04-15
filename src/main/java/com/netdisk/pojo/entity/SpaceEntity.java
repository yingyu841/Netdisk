package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 存储空间表实体。
 */
@Data
public class SpaceEntity {
    private Long id;
    private String spaceUuid;
    private Long ownerUserId;
    private String name;
    private String spaceType;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
