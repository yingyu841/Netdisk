package com.netdisk.service.impl;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.dm.model.v20151123.SingleSendMailRequest;
import com.aliyuncs.dm.model.v20151123.SingleSendMailResponse;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsRequest;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.profile.DefaultProfile;
import com.netdisk.common.ErrorCode;
import com.netdisk.common.exception.BizException;
import com.netdisk.config.AppProperties;
import com.netdisk.service.VerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码服务实现类。
 */
@Service
public class VerificationServiceImpl implements VerificationService {
    private static final Logger log = LoggerFactory.getLogger("biz.security.verification");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppProperties properties;
    private final Map<String, CodeEntry> codeStore = new ConcurrentHashMap<>();
    private final Map<String, Instant> sendGuard = new ConcurrentHashMap<>();

    public VerificationServiceImpl(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(String channel, String email, String mobile) {
        String key = buildKey(channel, email, mobile);
        Instant now = Instant.now();
        Instant last = sendGuard.get(key);
        if (last != null && now.minusSeconds(properties.getVerificationSendIntervalSeconds()).isBefore(last)) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS, 429, "发送过于频繁，请稍后再试");
        }
        sendGuard.put(key, now);

        String code = String.format("%06d", RANDOM.nextInt(1000000));
        codeStore.put(key, new CodeEntry(code, now.plusSeconds(properties.getVerificationCodeSeconds())));

        if ("email".equals(channel)) {
            sendEmail(email, code);
        } else {
            sendSms(mobile, code);
        }
    }

    @Override
    public boolean verifyAndConsume(String channel, String email, String mobile, String inputCode) {
        String key = buildKey(channel, email, mobile);
        CodeEntry entry = codeStore.get(key);
        if (entry == null || Instant.now().isAfter(entry.expireAt()) || !entry.code().equals(inputCode)) {
            return false;
        }
        codeStore.remove(key);
        return true;
    }

    private void sendSms(String mobile, String code) {
        AppProperties.Aliyun aliyun = properties.getAliyun();
        if (isBlank(aliyun.getAccessKeyId()) || isBlank(aliyun.getAccessKeySecret()) || isBlank(aliyun.getSmsSignName()) || isBlank(aliyun.getSmsTemplateCode())) {
            log.info("[MOCK SMS] mobile={} code={}", mobile, code);
            return;
        }
        try {
            DefaultProfile profile = DefaultProfile.getProfile(aliyun.getSmsRegion(), aliyun.getAccessKeyId(), aliyun.getAccessKeySecret());
            IAcsClient client = new DefaultAcsClient(profile);
            SendSmsRequest request = new SendSmsRequest();
            request.setPhoneNumbers(mobile);
            request.setSignName(aliyun.getSmsSignName());
            request.setTemplateCode(aliyun.getSmsTemplateCode());
            request.setTemplateParam("{\"code\":\"" + code + "\"}");
            SendSmsResponse response = client.getAcsResponse(request);
            if (!"OK".equalsIgnoreCase(response.getCode())) {
                throw new IllegalStateException(response.getMessage());
            }
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SEND_FAILED, 502, "验证码发送失败，请稍后重试");
        }
    }

    private void sendEmail(String email, String code) {
        AppProperties.Aliyun aliyun = properties.getAliyun();
        if (isBlank(aliyun.getAccessKeyId()) || isBlank(aliyun.getAccessKeySecret()) || isBlank(aliyun.getDmFromAddress())) {
            log.info("[MOCK EMAIL] email={} code={}", email, code);
            return;
        }
        try {
            DefaultProfile profile = DefaultProfile.getProfile(aliyun.getDmRegion(), aliyun.getAccessKeyId(), aliyun.getAccessKeySecret());
            IAcsClient client = new DefaultAcsClient(profile);
            SingleSendMailRequest request = new SingleSendMailRequest();
            request.setAccountName(aliyun.getDmFromAddress());
            request.setFromAlias(aliyun.getDmFromAlias());
            request.setAddressType(1);
            request.setReplyToAddress(Boolean.parseBoolean(aliyun.getDmReplyToAddress()));
            request.setToAddress(email);
            request.setSubject("登录验证码");
            request.setHtmlBody("<p>您的验证码为 <b>" + code + "</b>，请在 5 分钟内使用。</p>");
            SingleSendMailResponse response = client.getAcsResponse(request);
            if (response == null || response.getRequestId() == null) {
                throw new IllegalStateException("dm response empty");
            }
        } catch (Exception ex) {
            log.error("verification email send failed", ex);
            throw new BizException(ErrorCode.SEND_FAILED, 502, "验证码发送失败，请稍后重试");
        }
    }

    private String buildKey(String channel, String email, String mobile) {
        channel = safeLower(channel);
        if ("email".equals(channel) && !isBlank(email) && isBlank(mobile)) {
            return "email:" + safeLower(email);
        }
        if ("sms".equals(channel) && !isBlank(mobile) && isBlank(email)) {
            return "sms:" + mobile.trim();
        }
        throw new BizException(ErrorCode.INVALID_PARAM, 400, "手机号或邮箱填写不正确");
    }

    private boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }

    private String safeLower(String v) {
        return v == null ? "" : v.trim().toLowerCase();
    }

    private static class CodeEntry {
        private final String code;
        private final Instant expireAt;

        private CodeEntry(String code, Instant expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }

        private String code() {
            return code;
        }

        private Instant expireAt() {
            return expireAt;
        }
    }
}
