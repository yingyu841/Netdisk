package com.netdisk.pojo.dto;

import javax.validation.constraints.NotBlank;

/**
 * 分享提取码校验请求。
 */
public class ShareVerifyRequestDTO {
    @NotBlank(message = "code必填")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
