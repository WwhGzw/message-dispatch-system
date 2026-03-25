package com.msg.access.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 网关鉴权拦截器
 * 基于 HMAC-SHA256 签名验证调用方身份
 *
 * 请求头：
 * - X-App-Key: 调用方 AppKey
 * - X-Signature: HMAC-SHA256 签名
 * - X-Timestamp: 请求时间戳（毫秒）
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_APP_KEY = "X-App-Key";
    private static final String HEADER_SIGNATURE = "X-Signature";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";

    /** 时间戳有效窗口：5 分钟 */
    private static final long TIMESTAMP_TOLERANCE_MS = 5 * 60 * 1000L;

    /** 内存中的 AppKey → Secret 映射（生产环境应替换为数据库/配置中心） */
    private final Map<String, String> appKeySecretMap = new ConcurrentHashMap<>();

    public AuthInterceptor(@Value("${msg.auth.app-keys:}") String appKeysConfig) {
        parseAppKeysConfig(appKeysConfig);
    }

    /**
     * 注册 AppKey 和 Secret（用于测试或动态注册）
     */
    public void registerAppKey(String appKey, String secret) {
        appKeySecretMap.put(appKey, secret);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String appKey = request.getHeader(HEADER_APP_KEY);
        String signature = request.getHeader(HEADER_SIGNATURE);
        String timestamp = request.getHeader(HEADER_TIMESTAMP);

        // 检查必要的请求头
        if (appKey == null || signature == null || timestamp == null) {
            log.warn("Auth failed: missing required headers. appKey={}, signature={}, timestamp={}",
                    appKey != null, signature != null, timestamp != null);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 查找 Secret
        String secret = appKeySecretMap.get(appKey);
        if (secret == null) {
            log.warn("Auth failed: unknown appKey={}", appKey);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 检查时间戳新鲜度（防重放攻击）
        try {
            long ts = Long.parseLong(timestamp);
            long now = System.currentTimeMillis();
            if (Math.abs(now - ts) > TIMESTAMP_TOLERANCE_MS) {
                log.warn("Auth failed: timestamp expired. appKey={}, timestamp={}, now={}", appKey, ts, now);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }
        } catch (NumberFormatException e) {
            log.warn("Auth failed: invalid timestamp format. appKey={}, timestamp={}", appKey, timestamp);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        // 读取请求体
        String body = "";
        if (request instanceof ContentCachingRequestWrapper) {
            byte[] content = ((ContentCachingRequestWrapper) request).getContentAsByteArray();
            if (content.length > 0) {
                body = new String(content, StandardCharsets.UTF_8);
            }
        }

        // 验证签名: sign(secret, body + timestamp)
        String signContent = body + timestamp;
        if (!HmacSignatureUtil.verifySignature(secret, signContent, signature)) {
            log.warn("Auth failed: signature mismatch. appKey={}", appKey);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        log.debug("Auth success: appKey={}", appKey);
        return true;
    }

    /**
     * 解析配置格式: "appKey1:secret1,appKey2:secret2"
     */
    private void parseAppKeysConfig(String config) {
        if (config == null || config.trim().isEmpty()) {
            return;
        }
        for (String entry : config.split(",")) {
            String[] parts = entry.trim().split(":");
            if (parts.length == 2 && !parts[0].trim().isEmpty() && !parts[1].trim().isEmpty()) {
                appKeySecretMap.put(parts[0].trim(), parts[1].trim());
            }
        }
    }
}
