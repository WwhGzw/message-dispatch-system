package com.msg.center.service;

import com.msg.center.model.RouteResult;
import com.msg.common.entity.ChannelConfigEntity;
import com.msg.common.mapper.ChannelConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MessageRouter 单元测试 — 覆盖所有路由场景
 */
@ExtendWith(MockitoExtension.class)
class MessageRouterTest {

    @Mock
    private BlacklistService blacklistService;

    @Mock
    private WhitelistService whitelistService;

    @Mock
    private GrayRuleService grayRuleService;

    @Mock
    private ChannelConfigMapper channelConfigMapper;

    @InjectMocks
    private MessageRouter messageRouter;

    private static final String RECEIVER = "138xxxx1234";
    private static final String CHANNEL = "SMS";

    private ChannelConfigEntity highPriorityConfig;
    private ChannelConfigEntity lowPriorityConfig;

    @BeforeEach
    void setUp() {
        highPriorityConfig = ChannelConfigEntity.builder()
                .id(1L)
                .channelCode("SMS_ALIYUN")
                .channelType(CHANNEL)
                .channelName("阿里云短信")
                .priority(1)
                .weight(80)
                .enabled(true)
                .build();

        lowPriorityConfig = ChannelConfigEntity.builder()
                .id(2L)
                .channelCode("SMS_TENCENT")
                .channelType(CHANNEL)
                .channelName("腾讯云短信")
                .priority(2)
                .weight(20)
                .enabled(true)
                .build();
    }

    // ========== 黑名单拦截 ==========

    @Test
    void route_blacklistHit_returnsBlocked() {
        when(blacklistService.check(RECEIVER, CHANNEL)).thenReturn(true);

        RouteResult result = messageRouter.route(RECEIVER, CHANNEL);

        assertTrue(result.isBlocked());
        assertTrue(result.getReason().contains("黑名单拦截"));
        assertNull(result.getSelectedChannel());
        // 黑名单命中后不应继续检查白名单/灰度/渠道
        verify(whitelistService, never()).isEnabled(anyString());
        verify(grayRuleService, never()).isGrayEnabled(anyString());
        verify(channelConfigMapper, never()).selectEnabledByType(anyString());
    }

    // ========== 白名单模式拦截 ==========

    @Test
    void route_whitelistEnabledAndNotInWhitelist_returnsBlocked() {
        when(blacklistService.check(RECEIVER, CHANNEL)).thenReturn(false);
        when(whitelistService.isEnabled(CHANNEL)).thenReturn(true);
        when(whitelistService.check(RECEIVER, CHANNEL)).thenReturn(false);

        RouteResult result = messageRouter.route(RECEIVER, CHANNEL);

        assertTrue(result.isBlocked());
        assertTrue(result.getReason().contains("白名单模式"));
        verify(grayRuleService, never()).isGrayEnabled(anyString());
        verify(channelConfigMapper, never()).selectEnabledByType(anyString());
    }

    @Test
    void route_whitelistEnabledAndInWhitelist_continuesRouting() {
        when(blacklistService.check(RECEIVER, CHANNEL)).thenReturn(false);
        when(whitelistService.isEnabled(CHANNEL)).thenReturn(true);
        when(whitelistService.check(RECEIVER, CHANNEL)).thenReturn(true);
        when(grayRuleService.isGrayEnabled(CHANNEL)).thenReturn(false);
        when(channelConfigMapper.selectEnabledByType(CHANNEL))
                .thenReturn(List.of(highPriorityConfig));

        RouteResult result = messageRouter.route(RECEIVER, CHANNEL);

        assertFalse(result.isBlocked());
        assertNotNull(result.getSelectedChannel());
    }

    @Test
    void route_whitelistDisabled_skipsWhitelistCheck() {
        when(blacklistService.check(RECEIVER, CHANNEL)).thenReturn(false);
        when(whitelistService.isEnabled(CHANNEL)).thenReturn(false);
        when(grayRuleService.isGrayEnabled(CHANNEL)).thenReturn(false);
        when(channelConfigMapper.selectEnabledByType(CHANNEL))
                .thenReturn(List.of(highPriorityConfig));

        RouteResult result = messageRouter.route(RECEIVER, CHANNEL);

        assertFalse(result.isBlocked());
        verify(whitelistService, never()).check(anyString(), anyString());
    }

    // ========== 灰度规则拦截 ==========

    @Test
    void route_grayEnabledAndNotHit_returnsBlocked() {
        when(blacklistService.check(RECEIVER, CHANNEL)).thenReturn(false);
        when(whitelistService.isEnabled(CHANNEL)).thenReturn(false);
        when(grayRuleService.isGrayEnabled(CHANNEL)).thenReturn(true);
        when(grayRuleService.evaluate(RECEIVER, CHANNEL)).thenReturn(false);

        RouteResult result = messageRouter.route(RECEIVER, CHANNEL);

        assertTrue(result.isBlocked());
        assertTrue(result.getReason().contains("灰度规则未命中"));
        verify(channelConfigMapper, never()).selectEnabledByType(anyString());
    }

    @Test
    void route_grayEnabledAndHit_continuesRouting() {
        when(blacklistService.check(RECEIVER, CHANNEL)).thenReturn(false);
        when(whitelistService.isEnabled(CHANNEL)).thenReturn(false);
        when(grayRuleService.isGrayEnabled(CHANNEL)).thenReturn(true);
        when(grayRuleService.evaluate(RECEIVER, CHANNEL)).thenReturn(true);
        when(channelConfigMapper.selectEnabledByType(CHANNEL))
                .thenReturn(List.of(highPriorityConfig));

        RouteResult result = messageRouter.route(RECEIVER, CHANNEL);

        assertFalse(result.isBlocked());
        assertNotNull(result.getSelectedChannel());
    }

    @Test
    void route_grayDisabled_skipsGrayCheck() {
        when(blacklistService.check(RECEIVER, CHANNEL)).thenReturn(false);
        when(whitelistService.isEnabled(CHANNEL)).thenReturn(false);
        when(grayRuleService.isGrayEnabled(CHANNEL)).thenReturn(false);
        when(channelConfigMapper.selectEnabledByType(CHANNEL))
                .thenReturn(List.of(highPriorityConfig));

        RouteResult result = messageRouter.route(RECEIVER, CHANNEL);

        assertFalse(result.isBlocked());
        verify(grayRuleService, never()).evaluate(anyString(), anyString());
    }

    // ========== 无可用渠道配置 ==========

    @Test
    void route_noAvailableChannelConfigs_returnsBlocked() {
        when(blacklistService.check(RECEIVER, CHANNEL)).thenReturn(false);
        when(whitelistService.isEnabled(CHANNEL)).thenReturn(false);
        when(grayRuleService.isGrayEnabled(CHANNEL)).thenReturn(false);
        when(channelConfigMapper.selectEnabledByType(CHANNEL))
                .thenReturn(Collections.emptyList());

        RouteResult result = messageRouter.route(RECEIVER, CHANNEL);

        assertTrue(result.isBlocked());
        assertTrue(result.getReason().contains("无可用渠道配置"));
    }

    @Test
    void route_nullChannelConfigs_returnsBlocked() {
        when(blacklistService.check(RECEIVER, CHANNEL)).thenReturn(false);
        when(whitelistService.isEnabled(CHANNEL)).thenReturn(false);
        when(grayRuleService.isGrayEnabled(CHANNEL)).thenReturn(false);
        when(channelConfigMapper.selectEnabledByType(CHANNEL)).thenReturn(null);

        RouteResult result = messageRouter.route(RECEIVER, CHANNEL);

        assertTrue(result.isBlocked());
        assertTrue(result.getReason().contains("无可用渠道配置"));
    }

    // ========== 成功路由 — 优先级选择 ==========

    @Test
    void route_allChecksPassed_returnsHighestPriorityConfig() {
        when(blacklistService.check(RECEIVER, CHANNEL)).thenReturn(false);
        when(whitelistService.isEnabled(CHANNEL)).thenReturn(false);
        when(grayRuleService.isGrayEnabled(CHANNEL)).thenReturn(false);
        // 列表已按 priority ASC 排序，第一个是最高优先级
        when(channelConfigMapper.selectEnabledByType(CHANNEL))
                .thenReturn(Arrays.asList(highPriorityConfig, lowPriorityConfig));

        RouteResult result = messageRouter.route(RECEIVER, CHANNEL);

        assertFalse(result.isBlocked());
        assertNull(result.getReason());
        assertEquals(highPriorityConfig, result.getSelectedChannel());
        assertEquals("SMS_ALIYUN", result.getSelectedChannel().getChannelCode());
        assertEquals(1, result.getSelectedChannel().getPriority());
    }

    @Test
    void route_singleConfig_returnsThatConfig() {
        when(blacklistService.check(RECEIVER, CHANNEL)).thenReturn(false);
        when(whitelistService.isEnabled(CHANNEL)).thenReturn(false);
        when(grayRuleService.isGrayEnabled(CHANNEL)).thenReturn(false);
        when(channelConfigMapper.selectEnabledByType(CHANNEL))
                .thenReturn(List.of(lowPriorityConfig));

        RouteResult result = messageRouter.route(RECEIVER, CHANNEL);

        assertFalse(result.isBlocked());
        assertEquals(lowPriorityConfig, result.getSelectedChannel());
    }

    // ========== 组合场景：白名单 + 灰度同时启用 ==========

    @Test
    void route_whitelistAndGrayBothEnabled_bothPass_returnsSuccess() {
        when(blacklistService.check(RECEIVER, CHANNEL)).thenReturn(false);
        when(whitelistService.isEnabled(CHANNEL)).thenReturn(true);
        when(whitelistService.check(RECEIVER, CHANNEL)).thenReturn(true);
        when(grayRuleService.isGrayEnabled(CHANNEL)).thenReturn(true);
        when(grayRuleService.evaluate(RECEIVER, CHANNEL)).thenReturn(true);
        when(channelConfigMapper.selectEnabledByType(CHANNEL))
                .thenReturn(List.of(highPriorityConfig));

        RouteResult result = messageRouter.route(RECEIVER, CHANNEL);

        assertFalse(result.isBlocked());
        assertNotNull(result.getSelectedChannel());
    }

    @Test
    void route_whitelistPassButGrayFails_returnsBlocked() {
        when(blacklistService.check(RECEIVER, CHANNEL)).thenReturn(false);
        when(whitelistService.isEnabled(CHANNEL)).thenReturn(true);
        when(whitelistService.check(RECEIVER, CHANNEL)).thenReturn(true);
        when(grayRuleService.isGrayEnabled(CHANNEL)).thenReturn(true);
        when(grayRuleService.evaluate(RECEIVER, CHANNEL)).thenReturn(false);

        RouteResult result = messageRouter.route(RECEIVER, CHANNEL);

        assertTrue(result.isBlocked());
        assertTrue(result.getReason().contains("灰度规则未命中"));
    }
}
