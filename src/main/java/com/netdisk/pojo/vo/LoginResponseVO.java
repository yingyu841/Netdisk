package com.netdisk.pojo.vo;

public class LoginResponseVO {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserProfileVO user;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserProfileVO getUser() {
        return user;
    }

    public void setUser(UserProfileVO user) {
        this.user = user;
    }
}
