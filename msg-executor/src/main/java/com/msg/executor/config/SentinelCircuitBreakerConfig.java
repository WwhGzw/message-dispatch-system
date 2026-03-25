package com.msg.executor.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.msg.common.enums.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 渠道熔断配置
 * <p>
 * 为每个渠道类型配置熔断规则：
 * - 慢调用比例策略：响应时间 > 5000ms 视为慢调用
 * - 统计窗口 10 秒内慢调用比例 > 50% 触发熔断
 * - 熔断持续 30 秒后进入半开状态
 * <p>
 * 注：Redis 降级已在 IdempotentService 中实现（Redis 不可用时自动降级到 DB 幂等保障）。
 * 模板渲染失败处理已在 MessageCenterService 中实现（捕获 TemplateRenderException 后直接返回失败，不进入重试）。
 *
 * @see com.msg.common.enums.ChannelType
 */
@Configuration
public class SentinelCircuitBreakerConfig {

    private static final Logger log = LoggerFactory.getLogger(SentinelCircuitBreakerConfig.class);

    /** 慢调用阈值：5000ms */
    private static final int SLOW_REQUEST_RT_MS = 5000;

    /** 统计窗口：10 秒 */
    private static final int STAT_INTERVAL_MS = 10_000;

    /** 慢调用比例阈值：50% */
    private static final double SLOW_RATIO_THRESHOLD = 0.5;

    /** 熔断持续时间：30 秒 */
    private static final int CIRCUIT_BREAK_TIMEOUT_S = 30;

    /** 最小请求数（窗口内至少 5 个请求才触发熔断判断） */
    private static final int MIN_REQUEST_AMOUNT = 5;

    @PostConstruct
    public void initDegradeRules() {
        List<DegradeRule> rules = new ArrayList<>();

        for (ChannelType channelType : ChannelType.values()) {
            String resource = "channel:" + channelType.name();

            DegradeRule rule = new DegradeRule(resource)
                    .setGrade(RuleConstant.DEGRADE_GRADE_RT)
                    .setCount(SLOW_REQUEST_RT_MS)
                    .setTimeWindow(CIRCUIT_BREAK_TIMEOUT_S)
                    .setStatIntervalMs(STAT_INTERVAL_MS)
                    .setSlowRatioThreshold(SLOW_RATIO_THRESHOLD)
                    .setMinRequestAmount(MIN_REQUEST_AMOUNT);

            rules.add(rule);
            log.info("配置渠道熔断规则: resource={}, slowRt={}ms, ratio={}, window={}s",
                    resource, SLOW_REQUEST_RT_MS, SLOW_RATIO_THRESHOLD, CIRCUIT_BREAK_TIMEOUT_S);
        }

        DegradeRuleManager.loadRules(rules);
        log.info("Sentinel 渠道熔断规则加载完成, 共 {} 条规则", rules.size());
    }
}
