package com.msg.access.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sentinel BlockException 处理器
 * 返回 429 JSON 响应
 */
@Slf4j
public class RateLimitBlockHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 写入 429 限流响应
     *
     * @param response HTTP 响应
     * @param resource 被限流的资源名称
     */
    public static void writeBlockResponse(HttpServletResponse response, String resource) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 429);
        body.put("message", "请求过于频繁，请稍后重试");
        body.put("resource", resource);

        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(body));
        response.getWriter().flush();

        log.warn("Rate limited: resource={}", resource);
    }
}
