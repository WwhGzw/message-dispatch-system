package com.msg.center.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 白名单服务 — 白名单模式启用时，仅白名单内接收人可下发
 * 当前使用内存实现，后续可扩展为 Redis/DB 存储
 */
@Service
public class WhitelistService {

    /** 按渠道记录白名单是否启用 */
    private final Set<String> enabledChannels = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** 按渠道存储白名单接收人 */
    private final Map<String, Set<String>> whitelist = new ConcurrentHashMap<>();

    /**
     * 检查指定渠道是否启用了白名单模式
     */
    public boolean isEnabled(String channel) {
        return enabledChannels.contains(channel);
    }

    /**
     * 检查接收人是否在指定渠道的白名单中
     */
    public boolean check(String receiver, String channel) {
        Set<String> receivers = whitelist.get(channel);
        return receivers != null && receivers.contains(receiver);
    }

    /**
     * 启用指定渠道的白名单模式
     */
    public void enable(String channel) {
        enabledChannels.add(channel);
    }

    /**
     * 禁用指定渠道的白名单模式
     */
    public void disable(String channel) {
        enabledChannels.remove(channel);
    }

    /**
     * 添加白名单条目
     */
    public void add(String receiver, String channel) {
        whitelist.computeIfAbsent(channel, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(receiver);
    }
}
