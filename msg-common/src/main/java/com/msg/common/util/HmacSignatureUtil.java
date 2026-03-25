package com.msg.access.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * HMAC-SHA256 签名工具类
 * 用于 API 网关鉴权的签名生成与验证
 */
public final class HmacSignatureUtil {

    private static final String ALGORITHM = "HmacSHA256";

    private HmacSignatureUtil() {
    }

    /**
     * 生成 HMAC-SHA256 签名
     *
     * @param secret  密钥
     * @param content 待签名内容
     * @return 十六进制签名字符串
     */
    public static String generateSignature(String secret, String content) {
        if (secret == null || content == null) {
            throw new IllegalArgumentException("secret and content must not be null");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate HMAC-SHA256 signature", e);
        }
    }

    /**
     * 验证 HMAC-SHA256 签名
     *
     * @param secret    密钥
     * @param content   待验证内容
     * @param signature 待验证签名
     * @return true 如果签名匹配
     */
    public static boolean verifySignature(String secret, String content, String signature) {
        if (secret == null || content == null || signature == null) {
            return false;
        }
        String expected = generateSignature(secret, content);
        return expected.equalsIgnoreCase(signature);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
