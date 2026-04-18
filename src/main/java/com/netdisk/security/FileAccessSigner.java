package com.netdisk.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 本地文件预览/下载临时链接的 HMAC 签名（防篡改、短期有效）。
 */
public final class FileAccessSigner {
    private FileAccessSigner() {}

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * 签名明文：resourceId|userId|expEpochSeconds|mode ，mode 为 preview 或 download。
     */
    public static String sign(String secret, String resourceId, String userId, long expEpochSeconds, String mode) {
        String payload = resourceId + "|" + userId + "|" + expEpochSeconds + "|" + mode;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC sign failed", ex);
        }
    }

    public static boolean verify(String secret, String resourceId, String userId, long expEpochSeconds, String mode, String sigHex) {
        if (sigHex == null || sigHex.trim().isEmpty()) {
            return false;
        }
        String expected = sign(secret, resourceId, userId, expEpochSeconds, mode);
        return constantTimeEquals(expected.toLowerCase(), sigHex.trim().toLowerCase());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] x = a.getBytes(StandardCharsets.US_ASCII);
        byte[] y = b.getBytes(StandardCharsets.US_ASCII);
        if (x.length != y.length) {
            return false;
        }
        return MessageDigest.isEqual(x, y);
    }
}
