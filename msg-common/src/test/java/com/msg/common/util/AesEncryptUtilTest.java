package com.msg.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AesEncryptUtil 单元测试
 */
class AesEncryptUtilTest {

    // 32-byte key for AES-256
    private static final String KEY = "01234567890123456789012345678901";

    // ========== encrypt/decrypt round-trip ==========

    @Test
    void encryptDecrypt_roundTrip_returnsOriginal() {
        String plainText = "my-secret-token-value";
        String encrypted = AesEncryptUtil.encrypt(plainText, KEY);
        String decrypted = AesEncryptUtil.decrypt(encrypted, KEY);
        assertEquals(plainText, decrypted);
    }

    @Test
    void encryptDecrypt_emptyString_roundTrip() {
        String plainText = "";
        String encrypted = AesEncryptUtil.encrypt(plainText, KEY);
        String decrypted = AesEncryptUtil.decrypt(encrypted, KEY);
        assertEquals(plainText, decrypted);
    }

    @Test
    void encryptDecrypt_unicodeContent_roundTrip() {
        String plainText = "中文密钥测试🔑";
        String encrypted = AesEncryptUtil.encrypt(plainText, KEY);
        String decrypted = AesEncryptUtil.decrypt(encrypted, KEY);
        assertEquals(plainText, decrypted);
    }

    // ========== Different encryptions produce different ciphertexts (random IV) ==========

    @Test
    void encrypt_samePlainText_producesDifferentCiphertext() {
        String plainText = "same-value";
        String encrypted1 = AesEncryptUtil.encrypt(plainText, KEY);
        String encrypted2 = AesEncryptUtil.encrypt(plainText, KEY);
        assertNotEquals(encrypted1, encrypted2, "Random IV should produce different ciphertexts");
    }

    // ========== Wrong key fails decryption ==========

    @Test
    void decrypt_wrongKey_throwsException() {
        String plainText = "secret";
        String encrypted = AesEncryptUtil.encrypt(plainText, KEY);
        String wrongKey = "98765432109876543210987654321098";
        assertThrows(RuntimeException.class, () -> AesEncryptUtil.decrypt(encrypted, wrongKey));
    }

    // ========== Invalid key length ==========

    @Test
    void encrypt_shortKey_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> AesEncryptUtil.encrypt("text", "short-key"));
    }

    @Test
    void decrypt_shortKey_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> AesEncryptUtil.decrypt("dGVzdA==", "short-key"));
    }

    // ========== Null inputs ==========

    @Test
    void encrypt_nullPlainText_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> AesEncryptUtil.encrypt(null, KEY));
    }

    @Test
    void encrypt_nullKey_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> AesEncryptUtil.encrypt("text", null));
    }

    @Test
    void decrypt_nullCipherText_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> AesEncryptUtil.decrypt(null, KEY));
    }

    @Test
    void decrypt_nullKey_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> AesEncryptUtil.decrypt("dGVzdA==", null));
    }

    // ========== Invalid ciphertext ==========

    @Test
    void decrypt_tooShortCiphertext_throwsException() {
        // Base64 of less than 16 bytes
        String shortCipher = java.util.Base64.getEncoder().encodeToString(new byte[10]);
        assertThrows(IllegalArgumentException.class,
                () -> AesEncryptUtil.decrypt(shortCipher, KEY));
    }
}
