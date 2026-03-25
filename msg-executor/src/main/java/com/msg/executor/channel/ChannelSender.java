package com.msg.executor.channel;

import com.msg.common.enums.ChannelType;

/**
 * 渠道发送器统一抽象接口
 * 所有渠道实现（SMS/EMAIL/APP_PUSH/WEBHOOK）需实现此接口
 */
public interface ChannelSender {

    /**
     * 获取渠道类型
     */
    ChannelType getChannelType();

    /**
     * 执行消息下发
     *
     * @param context 下发上下文（含渲染后内容、接收人、渠道配置等）
     * @return 下发结果
     */
    SendChannelResult send(SendContext context);

    /**
     * 检查渠道健康状态
     *
     * @return 是否可用
     */
    boolean healthCheck();
}
