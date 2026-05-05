package com.netdisk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    /**
     * 是否启用慢接口日志与指标。
     */
    private boolean slowApiEnabled = true;
    /**
     * 慢接口阈值（毫秒）。
     */
    private long slowApiThresholdMs = 800L;
    private final ResponseCache responseCache = new ResponseCache();
    private final RateLimit rateLimit = new RateLimit();
    private final ChatMq chatMq = new ChatMq();
    private final Aliyun aliyun = new Aliyun();

    public static class ResponseCache {
        private boolean enabled = true;
        private long ttlSeconds = 12L;
        private long indexTtlSeconds = 300L;
        private int maxBodyBytes = 256 * 1024;
        private List<String> includePathPrefixes = new ArrayList<String>(Arrays.asList(
                "/api/v1/resources",
                "/api/v1/me/activities",
                "/api/v1/auth/sessions",
                "/api/v1/shares",
                "/api/v2/groups",
                "/api/v2/notifications",
                "/api/v2/chat",
                "/s/"
        ));
        private List<String> excludePathPrefixes = new ArrayList<String>(Arrays.asList(
                "/actuator"
        ));
        private List<String> excludePathSuffixes = new ArrayList<String>(Arrays.asList(
                "/preview-url",
                "/download-url",
                "/ws-token"
        ));
        private List<String> excludeExactPaths = new ArrayList<String>(Arrays.asList(
                "/api/v1/file-access"
        ));

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
        public long getIndexTtlSeconds() { return indexTtlSeconds; }
        public void setIndexTtlSeconds(long indexTtlSeconds) { this.indexTtlSeconds = indexTtlSeconds; }
        public int getMaxBodyBytes() { return maxBodyBytes; }
        public void setMaxBodyBytes(int maxBodyBytes) { this.maxBodyBytes = maxBodyBytes; }
        public List<String> getIncludePathPrefixes() { return includePathPrefixes; }
        public void setIncludePathPrefixes(List<String> includePathPrefixes) {
            this.includePathPrefixes = includePathPrefixes == null ? new ArrayList<String>() : includePathPrefixes;
        }
        public List<String> getExcludePathPrefixes() { return excludePathPrefixes; }
        public void setExcludePathPrefixes(List<String> excludePathPrefixes) {
            this.excludePathPrefixes = excludePathPrefixes == null ? new ArrayList<String>() : excludePathPrefixes;
        }
        public List<String> getExcludePathSuffixes() { return excludePathSuffixes; }
        public void setExcludePathSuffixes(List<String> excludePathSuffixes) {
            this.excludePathSuffixes = excludePathSuffixes == null ? new ArrayList<String>() : excludePathSuffixes;
        }
        public List<String> getExcludeExactPaths() { return excludeExactPaths; }
        public void setExcludeExactPaths(List<String> excludeExactPaths) {
            this.excludeExactPaths = excludeExactPaths == null ? new ArrayList<String>() : excludeExactPaths;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private List<Rule> rules = new ArrayList<Rule>(Arrays.asList(
                new Rule("auth:login", "POST", "/api/v1/auth/login", 20, 60),
                new Rule("auth:verification_send", "POST", "/api/v1/auth/verification/send", 10, 60),
                new Rule("share:verify", "POST", "/s/*/verify", 30, 60)
        ));

        public static class Rule {
            private String key;
            private String method;
            private String pathPattern;
            private int maxRequests;
            private int windowSeconds;

            public Rule() {
            }

            public Rule(String key, String method, String pathPattern, int maxRequests, int windowSeconds) {
                this.key = key;
                this.method = method;
                this.pathPattern = pathPattern;
                this.maxRequests = maxRequests;
                this.windowSeconds = windowSeconds;
            }

            public String getKey() { return key; }
            public void setKey(String key) { this.key = key; }
            public String getMethod() { return method; }
            public void setMethod(String method) { this.method = method; }
            public String getPathPattern() { return pathPattern; }
            public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }
            public int getMaxRequests() { return maxRequests; }
            public void setMaxRequests(int maxRequests) { this.maxRequests = maxRequests; }
            public int getWindowSeconds() { return windowSeconds; }
            public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public List<Rule> getRules() { return rules; }
        public void setRules(List<Rule> rules) {
            this.rules = rules == null ? new ArrayList<Rule>() : rules;
        }
    }

    public static class ChatMq {
        private boolean enabled = true;
        private String exchange = "netdisk.chat.exchange";
        private String queue = "netdisk.chat.message.sent.queue";
        private String routingKey = "chat.message.sent";
        private String readBatchQueue = "netdisk.chat.read.batch.queue";
        private String readBatchRoutingKey = "chat.read.batch";
        private String retryExchange = "netdisk.chat.retry.exchange";
        private String retryQueue = "netdisk.chat.retry.queue";
        private String retryRoutingKey = "#";
        private String deadExchange = "netdisk.chat.dead.exchange";
        private String deadQueue = "netdisk.chat.dead.queue";
        private String deadRoutingKey = "chat.dead";
        private int maxRetry = 3;
        private int retryDelayMs = 3000;
        private boolean outboxEnabled = true;
        private int outboxBatchSize = 100;
        private int outboxPollMs = 1000;
        private int outboxRetryDelaySeconds = 5;
        private int outboxCleanupMs = 3600000;
        private int outboxSentRetentionDays = 7;
        private int outboxMonitorMs = 10000;
        private int outboxWarnPendingThreshold = 5000;
        private int outboxWarnDeadThreshold = 100;
        private int consumerLogCleanupMs = 3600000;
        private int consumerLogRetentionDays = 7;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
        public String getQueue() { return queue; }
        public void setQueue(String queue) { this.queue = queue; }
        public String getRoutingKey() { return routingKey; }
        public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }
        public String getReadBatchQueue() { return readBatchQueue; }
        public void setReadBatchQueue(String readBatchQueue) { this.readBatchQueue = readBatchQueue; }
        public String getReadBatchRoutingKey() { return readBatchRoutingKey; }
        public void setReadBatchRoutingKey(String readBatchRoutingKey) { this.readBatchRoutingKey = readBatchRoutingKey; }
        public String getRetryExchange() { return retryExchange; }
        public void setRetryExchange(String retryExchange) { this.retryExchange = retryExchange; }
        public String getRetryQueue() { return retryQueue; }
        public void setRetryQueue(String retryQueue) { this.retryQueue = retryQueue; }
        public String getRetryRoutingKey() { return retryRoutingKey; }
        public void setRetryRoutingKey(String retryRoutingKey) { this.retryRoutingKey = retryRoutingKey; }
        public String getDeadExchange() { return deadExchange; }
        public void setDeadExchange(String deadExchange) { this.deadExchange = deadExchange; }
        public String getDeadQueue() { return deadQueue; }
        public void setDeadQueue(String deadQueue) { this.deadQueue = deadQueue; }
        public String getDeadRoutingKey() { return deadRoutingKey; }
        public void setDeadRoutingKey(String deadRoutingKey) { this.deadRoutingKey = deadRoutingKey; }
        public int getMaxRetry() { return maxRetry; }
        public void setMaxRetry(int maxRetry) { this.maxRetry = maxRetry; }
        public int getRetryDelayMs() { return retryDelayMs; }
        public void setRetryDelayMs(int retryDelayMs) { this.retryDelayMs = retryDelayMs; }
        public boolean isOutboxEnabled() { return outboxEnabled; }
        public void setOutboxEnabled(boolean outboxEnabled) { this.outboxEnabled = outboxEnabled; }
        public int getOutboxBatchSize() { return outboxBatchSize; }
        public void setOutboxBatchSize(int outboxBatchSize) { this.outboxBatchSize = outboxBatchSize; }
        public int getOutboxPollMs() { return outboxPollMs; }
        public void setOutboxPollMs(int outboxPollMs) { this.outboxPollMs = outboxPollMs; }
        public int getOutboxRetryDelaySeconds() { return outboxRetryDelaySeconds; }
        public void setOutboxRetryDelaySeconds(int outboxRetryDelaySeconds) { this.outboxRetryDelaySeconds = outboxRetryDelaySeconds; }
        public int getOutboxCleanupMs() { return outboxCleanupMs; }
        public void setOutboxCleanupMs(int outboxCleanupMs) { this.outboxCleanupMs = outboxCleanupMs; }
        public int getOutboxSentRetentionDays() { return outboxSentRetentionDays; }
        public void setOutboxSentRetentionDays(int outboxSentRetentionDays) { this.outboxSentRetentionDays = outboxSentRetentionDays; }
        public int getOutboxMonitorMs() { return outboxMonitorMs; }
        public void setOutboxMonitorMs(int outboxMonitorMs) { this.outboxMonitorMs = outboxMonitorMs; }
        public int getOutboxWarnPendingThreshold() { return outboxWarnPendingThreshold; }
        public void setOutboxWarnPendingThreshold(int outboxWarnPendingThreshold) { this.outboxWarnPendingThreshold = outboxWarnPendingThreshold; }
        public int getOutboxWarnDeadThreshold() { return outboxWarnDeadThreshold; }
        public void setOutboxWarnDeadThreshold(int outboxWarnDeadThreshold) { this.outboxWarnDeadThreshold = outboxWarnDeadThreshold; }
        public int getConsumerLogCleanupMs() { return consumerLogCleanupMs; }
        public void setConsumerLogCleanupMs(int consumerLogCleanupMs) { this.consumerLogCleanupMs = consumerLogCleanupMs; }
        public int getConsumerLogRetentionDays() { return consumerLogRetentionDays; }
        public void setConsumerLogRetentionDays(int consumerLogRetentionDays) { this.consumerLogRetentionDays = consumerLogRetentionDays; }
    }

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
    public boolean isSlowApiEnabled() { return slowApiEnabled; }
    public void setSlowApiEnabled(boolean slowApiEnabled) { this.slowApiEnabled = slowApiEnabled; }
    public long getSlowApiThresholdMs() { return slowApiThresholdMs; }
    public void setSlowApiThresholdMs(long slowApiThresholdMs) { this.slowApiThresholdMs = slowApiThresholdMs; }
    public ResponseCache getResponseCache() { return responseCache; }
    public RateLimit getRateLimit() { return rateLimit; }
    public ChatMq getChatMq() { return chatMq; }
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
