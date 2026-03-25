package com.msg.center.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灰度规则服务 — 控制消息下发范围，用于渐进式发布
 * 当前使用内存实现，后续可扩展为规则引擎
 */
@Service
public class GrayRuleService {

    /** 按渠道记录灰度是否启用 */
    private final Set<String> enabledChannels = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** 按渠道存储灰度命中的接收人 */
    private final Map<String, Set<String>> grayReceivers = new ConcurrentHashMap<>();

    /**
     * 检查指定渠道是否启用了灰度规则
     */
    public boolean isGrayEnabled(String channel) {
        return enabledChannels.contains(channel);
    }

    /**
     * 评估接收人是否命中灰度规则
     */
    public boolean evaluate(String receiver, String channel) {
        Set<String> receivers = grayReceivers.get(channel);
        return receivers != null && receivers.contains(receiver);
    }

    /**
     * 启用指定渠道的灰度规则
     */
    public void enableGray(String channel) {
        enabledChannels.add(channel);
    }

    /**
     * 禁用指定渠道的灰度规则
     */
    public void disableGray(String channel) {
        enabledChannels.remove(channel);
    }

    /**
     * 添加灰度命中接收人
     */
    public void addGrayReceiver(String receiver, String channel) {
        grayReceivers.computeIfAbsent(channel, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(receiver);
    }
}
