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
 * 邮件渠道发送器（Stub 实现）
 * TODO: 生产环境替换为实际邮件发送（JavaMail/SendGrid 等）
 */
@Component
public class EmailChannelSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(EmailChannelSender.class);

    /** 渠道 API 调用超时（秒） */
    @Value("${channel.email.timeout-seconds:5}")
    private int timeoutSeconds;

    @Override
    public ChannelType getChannelType() {
        return ChannelType.EMAIL;
    }

    @Override
    public SendChannelResult send(SendContext context) {
        try {
            log.info("邮件渠道下发: msgId={}, receiver={}, channel=EMAIL", context.getMsgId(), context.getReceiver());

            // TODO: 调用实际邮件发送 API，超时设置为 timeoutSeconds 秒
            String channelMsgId = UUID.randomUUID().toString();

            log.info("邮件渠道下发成功: msgId={}, channelMsgId={}", context.getMsgId(), channelMsgId);
            return SendChannelResult.success(channelMsgId);
        } catch (Exception e) {
            log.error("邮件渠道下发异常: msgId={}, error={}", context.getMsgId(), e.getMessage(), e);
            return SendChannelResult.fail("邮件下发异常: " + e.getMessage());
        }
    }

    @Override
    public boolean healthCheck() {
        // TODO: 生产环境检查邮件服务可达性
        return true;
    }
}
