package com.msg.center.service;

import com.msg.center.model.RouteResult;
import com.msg.common.entity.ChannelConfigEntity;
import com.msg.common.mapper.ChannelConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息路由器 — 黑白名单/灰度检查 + 渠道选择
 *
 * 路由算法：
 * 1. 黑名单检查 → 命中则拦截
 * 2. 白名单检查（启用时）→ 不在白名单则拦截
 * 3. 灰度规则检查（启用时）→ 未命中则拦截
 * 4. 查询已启用渠道配置（按优先级排序）
 * 5. 无可用配置 → 拦截
 * 6. 选择最高优先级配置 → 返回成功
 */
@Service
public class MessageRouter {

    @Autowired
    private BlacklistService blacklistService;

    @Autowired
    private WhitelistService whitelistService;

    @Autowired
    private GrayRuleService grayRuleService;

    @Autowired
    private ChannelConfigMapper channelConfigMapper;

    /**
     * 执行消息路由决策
     *
     * @param receiver 接收人标识
     * @param channel  渠道类型
     * @return 路由结果
     */
    public RouteResult route(String receiver, String channel) {
        // Step 1: 黑名单检查
        if (blacklistService.check(receiver, channel)) {
            return RouteResult.blocked("黑名单拦截: " + receiver);
        }

        // Step 2: 白名单检查（启用时仅白名单内可下发）
        if (whitelistService.isEnabled(channel)) {
            if (!whitelistService.check(receiver, channel)) {
                return RouteResult.blocked("白名单模式，接收人不在白名单内");
            }
        }

        // Step 3: 灰度规则检查（启用时未命中则拦截）
        if (grayRuleService.isGrayEnabled(channel)) {
            if (!grayRuleService.evaluate(receiver, channel)) {
                return RouteResult.blocked("灰度规则未命中");
            }
        }

        // Step 4: 查询已启用渠道配置（按优先级 ASC 排序）
        List<ChannelConfigEntity> configs = channelConfigMapper.selectEnabledByType(channel);
        if (configs == null || configs.isEmpty()) {
            return RouteResult.blocked("无可用渠道配置");
        }

        // Step 5: 选择最高优先级配置（列表已按 priority ASC 排序，第一个即最高优先级）
        ChannelConfigEntity selected = configs.get(0);

        return RouteResult.success(selected);
    }
}
