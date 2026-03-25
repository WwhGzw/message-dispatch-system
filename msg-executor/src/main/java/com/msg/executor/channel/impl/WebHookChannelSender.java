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
 * WebHook 渠道发送器（Stub 实现）
 * TODO: 生产环境替换为实际 HTTP POST 调用
 */
@Component
public class WebHookChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(WebHookChannelSender.class);

    /** 渠道 API 调用超时（秒） */
    @Value("${channel.webhook.timeout-seconds:5}")
    private int timeoutSeconds;

    @Override
    public ChannelType getChannelType() {
        return ChannelType.WEBHOOK;
    }

    @Override
    public SendChannelResult send(SendContext context) {
        try {
            log.info("WebHook渠道下发: msgId={}, receiver={}, channel=WEBHOOK", context.getMsgId(), context.getReceiver());

            // TODO: 执行 HTTP POST 到 receiver（webhook URL），超时设置为 timeoutSeconds 秒
            String channelMsgId = UUID.randomUUID().toString();

            log.info("WebHook渠道下发成功: msgId={}, channelMsgId={}", context.getMsgId(), channelMsgId);
            return SendChannelResult.success(channelMsgId);
        } catch (Exception e) {
            log.error("WebHook渠道下发异常: msgId={}, error={}", context.getMsgId(), e.getMessage(), e);
            return SendChannelResult.fail("WebHook下发异常: " + e.getMessage());
        }
    }

    @Override
    public boolean healthCheck() {
        // TODO: 生产环境检查 webhook 端点可达性
        return true;
    }
}
