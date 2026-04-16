package com.netdisk.pojo.dto;

import javax.validation.constraints.NotBlank;

public class RegisterRequest {
    private String email;
    private String mobile;
    @NotBlank(message = "verificationCode必填")
    private String verificationCode;
    @NotBlank(message = "password必填")
    private String password;
    @NotBlank(message = "nickname必填")
    private String nickname;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getVerificationCode() { return verificationCode; }
    public void setVerificationCode(String verificationCode) { this.verificationCode = verificationCode; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}
