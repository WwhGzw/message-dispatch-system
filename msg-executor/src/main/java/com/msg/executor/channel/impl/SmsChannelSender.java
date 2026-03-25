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
 * 短信渠道发送器（Stub 实现）
 * TODO: 生产环境替换为实际短信 API 调用（阿里云/腾讯云等）
 */
@Component
public class SmsChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(SmsChannelSender.class);

    /** 渠道 API 调用超时（秒） */
    @Value("${channel.sms.timeout-seconds:5}")
    private int timeoutSeconds;

    @Override
    public ChannelType getChannelType() {
        return ChannelType.SMS;
    }

    @Override
    public SendChannelResult send(SendContext context) {
        try {
            log.info("短信渠道下发: msgId={}, receiver={}, channel=SMS", context.getMsgId(), context.getReceiver());

            // TODO: 调用实际短信 API（如阿里云 SMS），超时设置为 timeoutSeconds 秒
            // 当前为 Stub 实现，模拟成功下发
            String channelMsgId = UUID.randomUUID().toString();

            log.info("短信渠道下发成功: msgId={}, channelMsgId={}", context.getMsgId(), channelMsgId);
            return SendChannelResult.success(channelMsgId);
        } catch (Exception e) {
            log.error("短信渠道下发异常: msgId={}, error={}", context.getMsgId(), e.getMessage(), e);
            return SendChannelResult.fail("短信下发异常: " + e.getMessage());
        }
    }

    @Override
    public boolean healthCheck() {
        // TODO: 生产环境检查短信 API 可达性
        return true;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
