package com.netdisk.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传分片表实体。
 */
@Data
public class UploadPart {
    private Long id;
    private Long uploadSessionId;
    private Integer partNumber;
    private Long partSize;
    private String etag;
    private String checksum;
    private String status;
    private LocalDateTime uploadedAt;
}
