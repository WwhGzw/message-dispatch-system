package com.msg.executor.engine;

import com.msg.common.enums.ChannelType;
import com.msg.executor.channel.ChannelSender;
import com.msg.executor.channel.SendChannelResult;
import com.msg.executor.channel.SendContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道执行引擎
 * 基于策略模式自动路由到对应渠道发送器。
 * Spring 自动注入所有 ChannelSender 实现，新渠道注册后自动纳入路由范围。
 */
@Service
public class ChannelExecutor {

    private static final Logger log = LoggerFactory.getLogger(ChannelExecutor.class);

    private final List<ChannelSender> channelSenders;
    private Map<ChannelType, ChannelSender> senderMap = Collections.emptyMap();

    private DeadLetterHandler deadLetterHandler;

    public ChannelExecutor(List<ChannelSender> channelSenders) {
        this.channelSenders = channelSenders != null ? channelSenders : Collections.emptyList();
    }

    @PostConstruct
    public void init() {
        Map<ChannelType, ChannelSender> map = new HashMap<>();
        for (ChannelSender sender : channelSenders) {
            ChannelType type = sender.getChannelType();
            if (map.containsKey(type)) {
                log.warn("重复注册渠道发送器，覆盖: channelType={}", type);
            }
            map.put(type, sender);
            log.info("注册渠道发送器: channelType={}, class={}", type, sender.getClass().getSimpleName());
        }
        this.senderMap = Collections.unmodifiableMap(map);
        log.info("渠道执行引擎初始化完成，已注册 {} 个渠道发送器", senderMap.size());
    }

    /**
     * 设置死信处理器（用于无对应渠道发送器时投递死信队列）
     */
    public void setDeadLetterHandler(DeadLetterHandler deadLetterHandler) {
        this.deadLetterHandler = deadLetterHandler;
    }

    /**
     * 根据渠道类型获取对应的发送器
     *
     * @param channelType 渠道类型
     * @return 渠道发送器，未找到时返回 null
     */
    public ChannelSender getSender(ChannelType channelType) {
        return senderMap.get(channelType);
    }

    /**
     * 执行消息下发（自动路由到对应渠道发送器）
     *
     * @param msgId         消息ID
     * @param channel       渠道类型字符串
     * @param content       渲染后的消息内容
     * @param receiver      接收人
     * @param channelConfig 渠道配置（JSON）
     * @return 下发结果，无对应发送器时返回失败结果
     */
    public SendChannelResult execute(String msgId, String channel, String content,
                                     String receiver, String channelConfig) {
        ChannelType channelType;
        try {
            channelType = ChannelType.valueOf(channel);
        } catch (IllegalArgumentException e) {
            String reason = "未知渠道类型: " + channel;
            log.error("渠道执行失败: msgId={}, {}", msgId, reason);
            submitToDeadLetter(msgId, reason);
            return SendChannelResult.fail(reason);
        }

        ChannelSender sender = getSender(channelType);
        if (sender == null) {
            String reason = "渠道发送器不存在: " + channel;
            log.error("渠道执行失败: msgId={}, {}", msgId, reason);
            submitToDeadLetter(msgId, reason);
            return SendChannelResult.fail(reason);
        }

        SendContext context = SendContext.builder()
                .msgId(msgId)
                .channel(channel)
                .content(content)
                .receiver(receiver)
                .channelConfig(channelConfig)
                .build();

        return sender.send(context);
    }

    private void submitToDeadLetter(String msgId, String reason) {
        if (deadLetterHandler != null) {
            deadLetterHandler.submitDeadLetter(msgId, reason);
        } else {
            log.warn("死信处理器未配置，无法投递死信: msgId={}, reason={}", msgId, reason);
        }
    }

    /**
     * 死信处理器回调接口
     */
    @FunctionalInterface
    public interface DeadLetterHandler {
        void submitDeadLetter(String msgId, String reason);
    }
}
