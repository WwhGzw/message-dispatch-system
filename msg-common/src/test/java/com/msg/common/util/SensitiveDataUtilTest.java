package com.msg.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SensitiveDataUtil 单元测试
 */
class SensitiveDataUtilTest {

    // ========== maskPhone ==========

    @Test
    void maskPhone_validPhone_masksMiddleDigits() {
        assertEquals("138****1234", SensitiveDataUtil.maskPhone("13812341234"));
    }

    @Test
    void maskPhone_anotherPhone_masksCorrectly() {
        assertEquals("159****8765", SensitiveDataUtil.maskPhone("15900008765"));
    }

    @Test
    void maskPhone_null_returnsNull() {
        assertNull(SensitiveDataUtil.maskPhone(null));
    }

    @Test
    void maskPhone_empty_returnsEmpty() {
        assertEquals("", SensitiveDataUtil.maskPhone(""));
    }

    @Test
    void maskPhone_invalidFormat_returnsOriginal() {
        assertEquals("12345", SensitiveDataUtil.maskPhone("12345"));
    }

    // ========== maskEmail ==========

    @Test
    void maskEmail_validEmail_masksLocalPart() {
        assertEquals("t***@example.com", SensitiveDataUtil.maskEmail("test@example.com"));
    }

    @Test
    void maskEmail_singleCharLocal_masksCorrectly() {
        assertEquals("a***@gmail.com", SensitiveDataUtil.maskEmail("a@gmail.com"));
    }

    @Test
    void maskEmail_longLocal_masksCorrectly() {
        assertEquals("l***@company.co.uk", SensitiveDataUtil.maskEmail("longusername@company.co.uk"));
    }

    @Test
    void maskEmail_null_returnsNull() {
        assertNull(SensitiveDataUtil.maskEmail(null));
    }

    @Test
    void maskEmail_empty_returnsEmpty() {
        assertEquals("", SensitiveDataUtil.maskEmail(""));
    }

    @Test
    void maskEmail_noAtSign_returnsOriginal() {
        assertEquals("notanemail", SensitiveDataUtil.maskEmail("notanemail"));
    }

    // ========== maskLog ==========

    @Test
    void maskLog_containsPhone_masksPhone() {
        String log = "发送短信到 13812345678 成功";
        String masked = SensitiveDataUtil.maskLog(log);
        assertTrue(masked.contains("138****5678"));
        assertFalse(masked.contains("13812345678"));
    }

    @Test
    void maskLog_containsEmail_masksEmail() {
        String log = "发送邮件到 test@example.com 成功";
        String masked = SensitiveDataUtil.maskLog(log);
        assertFalse(masked.contains("test@example.com"));
        assertTrue(masked.contains("***@example.com"));
    }

    @Test
    void maskLog_containsBoth_masksBoth() {
        String log = "用户 13812345678 邮箱 user@test.com 已通知";
        String masked = SensitiveDataUtil.maskLog(log);
        assertFalse(masked.contains("13812345678"));
        assertFalse(masked.contains("user@test.com"));
        assertTrue(masked.contains("138****5678"));
        assertTrue(masked.contains("***@test.com"));
    }

    @Test
    void maskLog_noSensitiveData_returnsOriginal() {
        String log = "系统启动完成，耗时 200ms";
        assertEquals(log, SensitiveDataUtil.maskLog(log));
    }

    @Test
    void maskLog_null_returnsNull() {
        assertNull(SensitiveDataUtil.maskLog(null));
    }

    @Test
    void maskLog_empty_returnsEmpty() {
        assertEquals("", SensitiveDataUtil.maskLog(""));
    }
}
