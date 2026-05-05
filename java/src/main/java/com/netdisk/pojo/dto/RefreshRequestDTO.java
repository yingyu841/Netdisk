package com.netdisk.pojo.dto;

import javax.validation.constraints.NotBlank;

public class RefreshRequestDTO {
    @NotBlank(message = "refreshToken必填")
    private String refreshToken;

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
}
