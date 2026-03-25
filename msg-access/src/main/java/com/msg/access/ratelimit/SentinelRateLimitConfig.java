package com.msg.access.ratelimit;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 多维度限流配置
 * 配置 API 级、AppKey 级、IP 级令牌桶限流规则
 */
@Slf4j
@Configuration
public class SentinelRateLimitConfig {

    @Value("${msg.ratelimit.api-qps:1000}")
    private double apiQps;

    @Value("${msg.ratelimit.appkey-qps:100}")
    private double appKeyQps;

    @Value("${msg.ratelimit.ip-qps:50}")
    private double ipQps;

    @PostConstruct
    public void init() {
        List<FlowRule> rules = new ArrayList<>();

        // API-level rate limiting
        String[] apiResources = {
                "/msg/send/now",
                "/msg/send/delay",
                "/msg/status",
                "/msg/cancel",
                "/msg/callback/receipt"
        };
        for (String resource : apiResources) {
            rules.add(createFlowRule(resource, apiQps));
        }

        // AppKey-level and IP-level rules are created dynamically
        // via RateLimitInterceptor using resource name patterns:
        //   appkey:{appKey}  and  ip:{clientIp}
        // We register a default template rule for each pattern prefix
        rules.add(createFlowRule("appkey:default", appKeyQps));
        rules.add(createFlowRule("ip:default", ipQps));

        FlowRuleManager.loadRules(rules);
        log.info("Sentinel rate limit rules loaded: {} rules, apiQps={}, appKeyQps={}, ipQps={}",
                rules.size(), apiQps, appKeyQps, ipQps);
    }

    private FlowRule createFlowRule(String resource, double qps) {
        FlowRule rule = new FlowRule();
        rule.setResource(resource);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(qps);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        return rule;
    }

    public double getAppKeyQps() {
        return appKeyQps;
    }

    public double getIpQps() {
        return ipQps;
    }
}
