package com.msg.center.model;

import com.msg.common.entity.ChannelConfigEntity;

/**
 * 消息路由结果 DTO
 */
public class RouteResult {

    private final boolean blocked;
    private final String reason;
    private final ChannelConfigEntity selectedChannel;

    private RouteResult(boolean blocked, String reason, ChannelConfigEntity selectedChannel) {
        this.blocked = blocked;
        this.reason = reason;
        this.selectedChannel = selectedChannel;
    }

    /**
     * 创建拦截结果
     */
    public static RouteResult blocked(String reason) {
        return new RouteResult(true, reason, null);
    }

    /**
     * 创建成功路由结果
     */
    public static RouteResult success(ChannelConfigEntity channelConfig) {
        return new RouteResult(false, null, channelConfig);
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String getReason() {
        return reason;
    }

    public ChannelConfigEntity getSelectedChannel() {
        return selectedChannel;
    }
}
