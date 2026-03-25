package com.msg.access.ratelimit;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimitInterceptor and SentinelRateLimitConfig
 */
class RateLimitInterceptorTest {

    private RateLimitInterceptor interceptor;
    private SentinelRateLimitConfig config;

    @BeforeEach
    void setUp() {
        // Clear any existing rules
        FlowRuleManager.loadRules(Collections.emptyList());

        config = new SentinelRateLimitConfig();
        // Use reflection-free approach: init will use default values
        config.init();

        interceptor = new RateLimitInterceptor(config);
    }

    @Test
    void preHandle_normalRequest_shouldPass() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/msg/send/now");
        request.addHeader("X-App-Key", "testApp");
        request.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
        assertNotEquals(429, response.getStatus());
    }

    @Test
    void preHandle_withoutAppKey_shouldStillPass() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/msg/send/now");
        request.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
    }

    @Test
    void preHandle_xForwardedFor_shouldExtractFirstIp() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/msg/send/now");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");
        request.setRemoteAddr("127.0.0.1");

        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, new Object());

        assertTrue(result);
    }

    @Test
    void afterCompletion_shouldExitEntriesGracefully() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/msg/send/now");
        request.addHeader("X-App-Key", "testApp2");
        request.setRemoteAddr("192.168.1.1");

        MockHttpServletResponse response = new MockHttpServletResponse();

        // First call preHandle to set entries
        interceptor.preHandle(request, response, new Object());

        // afterCompletion should not throw
        assertDoesNotThrow(() ->
                interceptor.afterCompletion(request, response, new Object(), null));
    }

    @Test
    void afterCompletion_noEntries_shouldNotThrow() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertDoesNotThrow(() ->
                interceptor.afterCompletion(request, response, new Object(), null));
    }

    @Test
    void blockHandler_shouldReturn429WithJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        RateLimitBlockHandler.writeBlockResponse(response, "/msg/send/now");

        assertEquals(429, response.getStatus());
        assertEquals("application/json;charset=UTF-8", response.getContentType());
        String body = response.getContentAsString();
        assertTrue(body.contains("429"));
        assertTrue(body.contains("请求过于频繁"));
        assertTrue(body.contains("/msg/send/now"));
    }
}
