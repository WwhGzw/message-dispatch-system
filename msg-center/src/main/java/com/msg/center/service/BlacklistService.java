package com.msg.center.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 黑名单服务 — 检查接收人是否在黑名单中
 * 当前使用内存 Set 实现，后续可扩展为 Redis/DB 存储
 */
@Service
public class BlacklistService {

    private final Set<String> blacklist = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * 检查接收人是否在指定渠道的黑名单中
     *
     * @param receiver 接收人标识
     * @param channel  渠道类型
     * @return true 表示命中黑名单
     */
    public boolean check(String receiver, String channel) {
        // key 格式: channel:receiver，支持按渠道隔离黑名单
        return blacklist.contains(buildKey(channel, receiver));
    }

    /**
     * 添加黑名单条目（供管理接口或测试使用）
     */
    public void add(String receiver, String channel) {
        blacklist.add(buildKey(channel, receiver));
    }

    /**
     * 移除黑名单条目
     */
    public void remove(String receiver, String channel) {
        blacklist.remove(buildKey(channel, receiver));
    }

    private String buildKey(String channel, String receiver) {
        return channel + ":" + receiver;
    }
}
