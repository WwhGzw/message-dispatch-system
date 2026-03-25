package com.msg.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256 加密/解密工具类
 * 用于渠道配置中 Secret/Token 的加密存储与运行时解密。
 * 使用 AES/CBC/PKCS5Padding，每次加密生成随机 IV 并前置于密文。
 */
public final class AesEncryptUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;
    private static final int KEY_LENGTH = 32; // 256-bit

    private AesEncryptUtil() {
    }

    /**
     * AES-256 加密
     *
     * @param plainText 明文
     * @param key       密钥（必须为32字节/256位）
     * @return Base64 编码的密文（IV + 加密数据）
     */
    public static String encrypt(String plainText, String key) {
        if (plainText == null || key == null) {
            throw new IllegalArgumentException("plainText and key must not be null");
        }
        validateKeyLength(key);

        try {
            // Generate random IV
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Prepend IV to ciphertext
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    /**
     * AES-256 解密
     *
     * @param cipherText Base64 编码的密文（IV + 加密数据）
     * @param key        密钥（必须为32字节/256位）
     * @return 原始明文
     */
    public static String decrypt(String cipherText, String key) {
        if (cipherText == null || key == null) {
            throw new IllegalArgumentException("cipherText and key must not be null");
        }
        validateKeyLength(key);

        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            if (combined.length < IV_LENGTH) {
                throw new IllegalArgumentException("Invalid ciphertext: too short");
            }

            // Extract IV and encrypted data
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed", e);
        }
    }

    private static void validateKeyLength(String key) {
        if (key.getBytes(StandardCharsets.UTF_8).length != KEY_LENGTH) {
            throw new IllegalArgumentException("Key must be exactly 32 bytes (256-bit) for AES-256");
        }
    }
}
