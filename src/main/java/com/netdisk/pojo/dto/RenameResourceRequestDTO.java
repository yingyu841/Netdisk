package com.netdisk.pojo.dto;

import javax.validation.constraints.NotBlank;

/**
 * 重命名资源请求DTO。
 */
public class RenameResourceRequestDTO {
    @NotBlank(message = "name必填")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
