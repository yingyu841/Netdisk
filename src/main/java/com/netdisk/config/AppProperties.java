package com.netdisk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String jwtSecret;
    private long accessTokenSeconds;
    private long refreshTokenSeconds;
    private long verificationCodeSeconds;
    private long verificationSendIntervalSeconds;
    private final Aliyun aliyun = new Aliyun();

    public static class Aliyun {
        private String accessKeyId;
        private String accessKeySecret;
        private String smsRegion;
        private String smsSignName;
        private String smsTemplateCode;
        private String dmRegion;
        private String dmFromAddress;
        private String dmFromAlias;
        private String dmReplyToAddress;

        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
        public String getAccessKeySecret() { return accessKeySecret; }
        public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
        public String getSmsRegion() { return smsRegion; }
        public void setSmsRegion(String smsRegion) { this.smsRegion = smsRegion; }
        public String getSmsSignName() { return smsSignName; }
        public void setSmsSignName(String smsSignName) { this.smsSignName = smsSignName; }
        public String getSmsTemplateCode() { return smsTemplateCode; }
        public void setSmsTemplateCode(String smsTemplateCode) { this.smsTemplateCode = smsTemplateCode; }
        public String getDmRegion() { return dmRegion; }
        public void setDmRegion(String dmRegion) { this.dmRegion = dmRegion; }
        public String getDmFromAddress() { return dmFromAddress; }
        public void setDmFromAddress(String dmFromAddress) { this.dmFromAddress = dmFromAddress; }
        public String getDmFromAlias() { return dmFromAlias; }
        public void setDmFromAlias(String dmFromAlias) { this.dmFromAlias = dmFromAlias; }
        public String getDmReplyToAddress() { return dmReplyToAddress; }
        public void setDmReplyToAddress(String dmReplyToAddress) { this.dmReplyToAddress = dmReplyToAddress; }
    }

    public String getJwtSecret() { return jwtSecret; }
    public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
    public long getAccessTokenSeconds() { return accessTokenSeconds; }
    public void setAccessTokenSeconds(long accessTokenSeconds) { this.accessTokenSeconds = accessTokenSeconds; }
    public long getRefreshTokenSeconds() { return refreshTokenSeconds; }
    public void setRefreshTokenSeconds(long refreshTokenSeconds) { this.refreshTokenSeconds = refreshTokenSeconds; }
    public long getVerificationCodeSeconds() { return verificationCodeSeconds; }
    public void setVerificationCodeSeconds(long verificationCodeSeconds) { this.verificationCodeSeconds = verificationCodeSeconds; }
    public long getVerificationSendIntervalSeconds() { return verificationSendIntervalSeconds; }
    public void setVerificationSendIntervalSeconds(long verificationSendIntervalSeconds) { this.verificationSendIntervalSeconds = verificationSendIntervalSeconds; }
    public Aliyun getAliyun() { return aliyun; }
}
