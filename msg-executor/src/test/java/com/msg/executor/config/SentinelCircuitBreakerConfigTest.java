package com.msg.executor.config;

import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.msg.common.enums.ChannelType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SentinelCircuitBreakerConfig 单元测试
 */
class SentinelCircuitBreakerConfigTest {

    private SentinelCircuitBreakerConfig config;

    @BeforeEach
    void setUp() {
        // Clear any existing rules
        DegradeRuleManager.loadRules(null);
        config = new SentinelCircuitBreakerConfig();
    }

    @Test
    void initDegradeRules_shouldLoadRulesForAllChannelTypes() {
        config.initDegradeRules();

        List<DegradeRule> rules = DegradeRuleManager.getRules();
        assertEquals(ChannelType.values().length, rules.size(),
                "Should have one rule per channel type");
    }

    @Test
    void initDegradeRules_shouldConfigureCorrectResourceNames() {
        config.initDegradeRules();

        List<DegradeRule> rules = DegradeRuleManager.getRules();
        for (ChannelType channelType : ChannelType.values()) {
            String expectedResource = "channel:" + channelType.name();
            boolean found = rules.stream()
                    .anyMatch(r -> expectedResource.equals(r.getResource()));
            assertTrue(found, "Should have rule for resource: " + expectedResource);
        }
    }

    @Test
    void initDegradeRules_shouldConfigureSlowRequestThreshold() {
        config.initDegradeRules();

        List<DegradeRule> rules = DegradeRuleManager.getRules();
        for (DegradeRule rule : rules) {
            assertEquals(5000, rule.getCount(),
                    "Slow request RT threshold should be 5000ms");
            assertEquals(0.5, rule.getSlowRatioThreshold(), 0.001,
                    "Slow ratio threshold should be 50%");
            assertEquals(30, rule.getTimeWindow(),
                    "Circuit break timeout should be 30 seconds");
            assertEquals(10_000, rule.getStatIntervalMs(),
                    "Stat interval should be 10 seconds");
        }
    }
}
