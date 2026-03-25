package com.msg.executor.channel.impl;

import com.msg.common.enums.ChannelType;
import com.msg.executor.channel.ChannelSender;
import com.msg.executor.channel.SendChannelResult;
import com.msg.executor.channel.SendContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * App 推送渠道发送器（Stub 实现）
 * TODO: 生产环境替换为实际推送 API（极光/个推/Firebase 等）
 */
@Component
public class AppPushChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(AppPushChannelSender.class);

    /** 渠道 API 调用超时（秒） */
    @Value("${channel.app-push.timeout-seconds:5}")
    private int timeoutSeconds;

    @Override
    public ChannelType getChannelType() {
        return ChannelType.APP_PUSH;
    }

    @Override
    public SendChannelResult send(SendContext context) {
        try {
            log.info("App推送渠道下发: msgId={}, receiver={}, channel=APP_PUSH", context.getMsgId(), context.getReceiver());

            // TODO: 调用实际推送 API，超时设置为 timeoutSeconds 秒
            String channelMsgId = UUID.randomUUID().toString();

            log.info("App推送渠道下发成功: msgId={}, channelMsgId={}", context.getMsgId(), channelMsgId);
            return SendChannelResult.success(channelMsgId);
        } catch (Exception e) {
            log.error("App推送渠道下发异常: msgId={}, error={}", context.getMsgId(), e.getMessage(), e);
            return SendChannelResult.fail("App推送下发异常: " + e.getMessage());
        }
    }

    @Override
    public boolean healthCheck() {
        // TODO: 生产环境检查推送服务可达性
        return true;
    }
}
