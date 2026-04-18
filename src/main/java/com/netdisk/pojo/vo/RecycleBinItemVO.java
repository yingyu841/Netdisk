package com.netdisk.pojo.vo;

/**
 * 回收站列表项。
 */
public class RecycleBinItemVO {
    private String id;
    private String name;
    private String type;
    private Long size;
    private String extension;
    private String updatedAt;
    private String deletedAt;
    private String purgeAt;
    private String originalParentId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(String deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getPurgeAt() {
        return purgeAt;
    }

    public void setPurgeAt(String purgeAt) {
        this.purgeAt = purgeAt;
    }

    public String getOriginalParentId() {
        return originalParentId;
    }

    public void setOriginalParentId(String originalParentId) {
        this.originalParentId = originalParentId;
    }
}
