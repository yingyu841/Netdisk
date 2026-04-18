package com.netdisk.pojo.dto;

import javax.validation.constraints.NotBlank;

public class SendVerificationRequestDTO {
    @NotBlank(message = "channel必填")
    private String channel;
    private String email;
    private String mobile;

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
}
