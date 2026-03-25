package com.msg.access.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HmacSignatureUtil 单元测试
 */
class HmacSignatureUtilTest {

    private static final String SECRET = "test-secret-key-123";
    private static final String CONTENT = "hello world1700000000000";

    @Test
    @DisplayName("generateSignature should produce a non-empty hex string")
    void generateSignature_producesNonEmptyHex() {
        String signature = HmacSignatureUtil.generateSignature(SECRET, CONTENT);

        assertNotNull(signature);
        assertFalse(signature.isEmpty());
        // HMAC-SHA256 produces 32 bytes = 64 hex chars
        assertEquals(64, signature.length());
        assertTrue(signature.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("generateSignature should be deterministic for same inputs")
    void generateSignature_deterministic() {
        String sig1 = HmacSignatureUtil.generateSignature(SECRET, CONTENT);
        String sig2 = HmacSignatureUtil.generateSignature(SECRET, CONTENT);

        assertEquals(sig1, sig2);
    }

    @Test
    @DisplayName("verifySignature should return true for correct secret and content")
    void verifySignature_success() {
        String signature = HmacSignatureUtil.generateSignature(SECRET, CONTENT);

        assertTrue(HmacSignatureUtil.verifySignature(SECRET, CONTENT, signature));
    }

    @Test
    @DisplayName("verifySignature should return false for wrong secret")
    void verifySignature_failsWithWrongSecret() {
        String signature = HmacSignatureUtil.generateSignature(SECRET, CONTENT);

        assertFalse(HmacSignatureUtil.verifySignature("wrong-secret", CONTENT, signature));
    }

    @Test
    @DisplayName("verifySignature should return false for wrong content")
    void verifySignature_failsWithWrongContent() {
        String signature = HmacSignatureUtil.generateSignature(SECRET, CONTENT);

        assertFalse(HmacSignatureUtil.verifySignature(SECRET, "different content", signature));
    }

    @Test
    @DisplayName("verifySignature should return false for tampered signature")
    void verifySignature_failsWithTamperedSignature() {
        assertFalse(HmacSignatureUtil.verifySignature(SECRET, CONTENT, "0000000000000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    @DisplayName("verifySignature should be case-insensitive for signature comparison")
    void verifySignature_caseInsensitive() {
        String signature = HmacSignatureUtil.generateSignature(SECRET, CONTENT);

        assertTrue(HmacSignatureUtil.verifySignature(SECRET, CONTENT, signature.toUpperCase()));
    }

    @Test
    @DisplayName("generateSignature should throw on null secret")
    void generateSignature_throwsOnNullSecret() {
        assertThrows(IllegalArgumentException.class,
                () -> HmacSignatureUtil.generateSignature(null, CONTENT));
    }

    @Test
    @DisplayName("generateSignature should throw on null content")
    void generateSignature_throwsOnNullContent() {
        assertThrows(IllegalArgumentException.class,
                () -> HmacSignatureUtil.generateSignature(SECRET, null));
    }

    @Test
    @DisplayName("verifySignature should return false on null inputs")
    void verifySignature_returnsFalseOnNullInputs() {
        assertFalse(HmacSignatureUtil.verifySignature(null, CONTENT, "sig"));
        assertFalse(HmacSignatureUtil.verifySignature(SECRET, null, "sig"));
        assertFalse(HmacSignatureUtil.verifySignature(SECRET, CONTENT, null));
    }

    @Test
    @DisplayName("Different secrets should produce different signatures")
    void differentSecrets_produceDifferentSignatures() {
        String sig1 = HmacSignatureUtil.generateSignature("secret-a", CONTENT);
        String sig2 = HmacSignatureUtil.generateSignature("secret-b", CONTENT);

        assertNotEquals(sig1, sig2);
    }

    @Test
    @DisplayName("generateSignature should handle empty content")
    void generateSignature_handlesEmptyContent() {
        String signature = HmacSignatureUtil.generateSignature(SECRET, "");

        assertNotNull(signature);
        assertEquals(64, signature.length());
        assertTrue(HmacSignatureUtil.verifySignature(SECRET, "", signature));
    }
}
