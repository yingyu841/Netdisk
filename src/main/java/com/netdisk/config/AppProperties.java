package com.netdisk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private String jwtSecret;
    private long accessTokenSeconds;
    private long refreshTokenSeconds;
    private long verificationCodeSeconds;
    private long verificationSendIntervalSeconds;
    /**
     * 本地文件访问签名 URL 有效期（秒），用于预览/下载临时链接。
     */
    private long fileAccessTtlSeconds = 3600L;
    /**
     * 文件访问签名密钥；为空则回退为 jwtSecret。
     */
    private String fileAccessSecret;
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
    public long getFileAccessTtlSeconds() { return fileAccessTtlSeconds; }
    public void setFileAccessTtlSeconds(long fileAccessTtlSeconds) { this.fileAccessTtlSeconds = fileAccessTtlSeconds; }
    public String getFileAccessSecret() { return fileAccessSecret; }
    public void setFileAccessSecret(String fileAccessSecret) { this.fileAccessSecret = fileAccessSecret; }
    public Aliyun getAliyun() { return aliyun; }

    /**
     * 用于 HMAC 签名的密钥，未单独配置时与 JWT 密钥相同。
     */
    public String resolveFileAccessSecret() {
        if (fileAccessSecret != null && !fileAccessSecret.trim().isEmpty()) {
            return fileAccessSecret.trim();
        }
        return jwtSecret;
    }
}
