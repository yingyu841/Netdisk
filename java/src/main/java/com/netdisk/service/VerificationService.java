package com.netdisk.service;

/**
 * 验证码服务接口。
 */
public interface VerificationService {
    /**
     * 发送验证码。
     *
     * @param channel 渠道：email/sms
     * @param email 邮箱
     * @param mobile 手机号
     */
    void send(String channel, String email, String mobile);

    /**
     * 校验并消费验证码。
     *
     * @param channel 渠道：email/sms
     * @param email 邮箱
     * @param mobile 手机号
     * @param inputCode 输入验证码
     * @return 是否校验通过
     */
    boolean verifyAndConsume(String channel, String email, String mobile, String inputCode);
}
