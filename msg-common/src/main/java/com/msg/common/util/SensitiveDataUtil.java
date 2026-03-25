package com.msg.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具类
 * 对手机号、邮箱等敏感信息进行脱敏处理。
 */
public final class SensitiveDataUtil {

    /** 中国大陆手机号正则 (11位数字，1开头) */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");

    /** 邮箱正则 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("([a-zA-Z0-9_.-])([a-zA-Z0-9_.+-]*)@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");

    private SensitiveDataUtil() {
    }

    /**
     * 手机号脱敏：保留前3位和后4位，中间用****替换
     * 例如：13812345678 → 138****5678
     *
     * @param phone 手机号
     * @return 脱敏后的手机号，null/空串原样返回
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.isEmpty()) {
            return phone;
        }
        Matcher matcher = PHONE_PATTERN.matcher(phone);
        if (matcher.matches()) {
            return matcher.group(1) + "****" + matcher.group(2);
        }
        return phone;
    }

    /**
     * 邮箱脱敏：保留首字符和@后域名，中间用***替换
     * 例如：test@example.com → t***@example.com
     *
     * @param email 邮箱地址
     * @return 脱敏后的邮箱，null/空串原样返回
     */
    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return email;
        }
        return email.charAt(0) + "***@" + email.substring(atIndex + 1);
    }

    /**
     * 日志内容脱敏：替换日志中所有手机号和邮箱
     *
     * @param logContent 日志内容
     * @return 脱敏后的日志内容
     */
    public static String maskLog(String logContent) {
        if (logContent == null || logContent.isEmpty()) {
            return logContent;
        }
        // Mask phone numbers
        String result = PHONE_PATTERN.matcher(logContent).replaceAll("$1****$2");
        // Mask emails
        result = EMAIL_PATTERN.matcher(result).replaceAll("$1***@$3");
        return result;
    }
}
