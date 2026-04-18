package com.netdisk.pojo.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 初始化上传请求。
 */
public class UploadInitRequestDTO {
    private String parentId;
    @NotBlank(message = "filename必填")
    private String filename;
    @NotNull(message = "size必填")
    @Min(value = 1, message = "size必须大于0")
    private Long size;
    private String sha256;
    @NotNull(message = "partSize必填")
    @Min(value = 1, message = "partSize必须大于0")
    private Long partSize;

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public Long getPartSize() {
        return partSize;
    }

    public void setPartSize(Long partSize) {
        this.partSize = partSize;
    }
}
