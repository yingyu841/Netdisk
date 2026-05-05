package com.netdisk.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

/**
 * 分享提取码校验 token 签名工具（无状态短期有效）。
 */
public final class ShareVerifyTokenSigner {
    private ShareVerifyTokenSigner() {}

    private static final String HMAC_SHA256 = "HmacSHA256";

    public static String issue(String secret, String shareCode, long ttlSeconds) {
        long ttl = ttlSeconds <= 0L ? 3600L : ttlSeconds;
        long exp = Instant.now().getEpochSecond() + ttl;
        String sig = sign(secret, shareCode, exp);
        return exp + "." + sig;
    }

    public static boolean verify(String secret, String shareCode, String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        String[] arr = token.trim().split("\\.");
        if (arr.length != 2) {
            return false;
        }
        long exp;
        try {
            exp = Long.parseLong(arr[0]);
        } catch (Exception ex) {
            return false;
        }
        if (exp <= Instant.now().getEpochSecond()) {
            return false;
        }
        String expected = sign(secret, shareCode, exp);
        return constantTimeEquals(expected, arr[1]);
    }

    private static String sign(String secret, String shareCode, long expEpochSeconds) {
        String payload = (shareCode == null ? "" : shareCode.trim()) + "|" + expEpochSeconds;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC sign failed", ex);
        }
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
