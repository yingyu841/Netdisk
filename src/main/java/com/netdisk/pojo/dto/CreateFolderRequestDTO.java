package com.netdisk.pojo.dto;

import javax.validation.constraints.NotBlank;

/**
 * 新建文件夹请求。
 */
public class CreateFolderRequestDTO {
    private String parentId;

    @NotBlank(message = "name必填")
    private String name;

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
