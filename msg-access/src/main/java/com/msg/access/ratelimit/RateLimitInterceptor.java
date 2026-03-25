package com.msg.access.ratelimit;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多维度限流拦截器
 * 依次检查 API 级、AppKey 级、IP 级限流
 * 任一维度被限流则返回 429
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String HEADER_APP_KEY = "X-App-Key";

    private final SentinelRateLimitConfig rateLimitConfig;

    /** Track dynamically registered resources to avoid duplicate rule registration */
    private final Set<String> registeredResources = ConcurrentHashMap.newKeySet();

    public RateLimitInterceptor(SentinelRateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        String uri = request.getRequestURI();
        String appKey = request.getHeader(HEADER_APP_KEY);
        String clientIp = getClientIp(request);

        // Dimension 1: API-level rate limiting
        Entry apiEntry = null;
        try {
            apiEntry = SphU.entry(uri);
        } catch (BlockException e) {
            RateLimitBlockHandler.writeBlockResponse(response, uri);
            return false;
        }

        // Dimension 2: AppKey-level rate limiting
        Entry appKeyEntry = null;
        if (appKey != null && !appKey.isEmpty()) {
            String appKeyResource = "appkey:" + appKey;
            ensureRuleExists(appKeyResource, rateLimitConfig.getAppKeyQps());
            try {
                appKeyEntry = SphU.entry(appKeyResource);
            } catch (BlockException e) {
                if (apiEntry != null) {
                    apiEntry.exit();
                }
                RateLimitBlockHandler.writeBlockResponse(response, appKeyResource);
                return false;
            }
        }

        // Dimension 3: IP-level rate limiting
        Entry ipEntry = null;
        if (clientIp != null && !clientIp.isEmpty()) {
            String ipResource = "ip:" + clientIp;
            ensureRuleExists(ipResource, rateLimitConfig.getIpQps());
            try {
                ipEntry = SphU.entry(ipResource);
            } catch (BlockException e) {
                if (appKeyEntry != null) {
                    appKeyEntry.exit();
                }
                if (apiEntry != null) {
                    apiEntry.exit();
                }
                RateLimitBlockHandler.writeBlockResponse(response, ipResource);
                return false;
            }
        }

        // Store entries in request attributes for cleanup in afterCompletion
        request.setAttribute("_sentinel_api_entry", apiEntry);
        request.setAttribute("_sentinel_appkey_entry", appKeyEntry);
        request.setAttribute("_sentinel_ip_entry", ipEntry);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        exitEntry(request, "_sentinel_ip_entry");
        exitEntry(request, "_sentinel_appkey_entry");
        exitEntry(request, "_sentinel_api_entry");
    }

    private void exitEntry(HttpServletRequest request, String attrName) {
        Entry entry = (Entry) request.getAttribute(attrName);
        if (entry != null) {
            entry.exit();
        }
    }

    /**
     * Dynamically register a flow rule for a resource if not already registered.
     */
    private void ensureRuleExists(String resource, double qps) {
        if (registeredResources.add(resource)) {
            List<FlowRule> currentRules = new ArrayList<>(FlowRuleManager.getRules());
            FlowRule rule = new FlowRule();
            rule.setResource(resource);
            rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
            rule.setCount(qps);
            rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
            currentRules.add(rule);
            FlowRuleManager.loadRules(currentRules);
        }
    }

    /**
     * 获取客户端真实 IP
     * 支持 X-Forwarded-For、X-Real-IP 代理头
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For may contain multiple IPs, take the first one
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
