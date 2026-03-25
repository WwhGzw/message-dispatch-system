package com.msg.executor.consumer;

import com.msg.common.entity.MessageEntity;
import com.msg.common.enums.ChannelType;
import com.msg.common.enums.MessageStatus;
import com.msg.common.mapper.MessageMapper;
import com.msg.executor.channel.ChannelSender;
import com.msg.executor.channel.SendChannelResult;
import com.msg.executor.channel.SendContext;
import com.msg.executor.engine.ChannelExecutor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeoutException;

/**
 * RocketMQ 消息消费者
 * 消费 MSG_SEND 主题消息，执行渠道下发流程。
 * 包含消费端幂等校验、渠道执行、成功/失败处理。
 */
@Service
@RocketMQMessageListener(topic = "MSG_SEND", consumerGroup = "msg-executor-consumer-group")
public class MessageConsumer implements RocketMQListener<String> {

    private static final Logger log = LoggerFactory.getLogger(MessageConsumer.class);

    private static final String TOPIC_MSG_RETRY = "MSG_RETRY";
    private static final String TOPIC_MSG_DEAD_LETTER = "MSG_DEAD_LETTER";

    private final MessageMapper messageMapper;
    private final ChannelExecutor channelExecutor;
    private final RocketMQTemplate rocketMQTemplate;

    public MessageConsumer(MessageMapper messageMapper,
                           ChannelExecutor channelExecutor,
                           RocketMQTemplate rocketMQTemplate) {
        this.messageMapper = messageMapper;
        this.channelExecutor = channelExecutor;
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Override
    public void onMessage(String msgId) {
        log.info("消费消息: msgId={}", msgId);

        // Step 1: Query message from DB
        MessageEntity msg = messageMapper.selectByMsgId(msgId);
        if (msg == null) {
            log.warn("消息不存在，丢弃: msgId={}", msgId);
            return;
        }

        // Step 2: Consumer-side idempotency — skip terminal states
        MessageStatus status = MessageStatus.valueOf(msg.getStatus());
        if (status.isTerminal()) {
            log.info("消息已终态，跳过: msgId={}, status={}", msgId, msg.getStatus());
            return;
        }

        // Step 3: Get channel sender via ChannelExecutor
        ChannelType channelType;
        try {
            channelType = ChannelType.valueOf(msg.getChannel());
        } catch (IllegalArgumentException e) {
            log.error("未知渠道类型: msgId={}, channel={}", msgId, msg.getChannel());
            submitDeadLetter(msgId, "未知渠道类型: " + msg.getChannel());
            return;
        }

        ChannelSender sender = channelExecutor.getSender(channelType);
        if (sender == null) {
            log.error("渠道发送器不存在: msgId={}, channel={}", msgId, msg.getChannel());
            submitDeadLetter(msgId, "渠道发送器不存在: " + msg.getChannel());
            return;
        }

        // Step 4: Build SendContext
        SendContext context = SendContext.builder()
                .msgId(msg.getMsgId())
                .channel(msg.getChannel())
                .content(msg.getContent())
                .receiver(msg.getReceiver())
                .channelConfig(msg.getExtParams())
                .build();

        // Step 5: Execute send with timeout handling
        try {
            SendChannelResult result = sender.send(context);

            if (result.isSuccess()) {
                // Success: update status to SUCCESS and record actualSendTime
                messageMapper.updateStatus(msgId, MessageStatus.SENDING.name(), MessageStatus.SUCCESS.name());
                messageMapper.updateActualSendTime(msgId, LocalDateTime.now());
                log.info("消息下发成功: msgId={}", msgId);
            } else {
                // Failure: enter retry/dead letter flow
                handleSendFailure(msg, result.getErrorMessage());
            }
        } catch (Exception e) {
            // Timeout or other exception: enter retry flow
            if (isTimeoutException(e)) {
                log.error("渠道API超时: msgId={}", msgId, e);
            } else {
                log.error("渠道下发异常: msgId={}", msgId, e);
            }
            handleSendFailure(msg, e.getMessage());
        }
    }

    /**
     * 处理下发失败：重试或进入死信队列
     */
    void handleSendFailure(MessageEntity msg, String errorMessage) {
        String msgId = msg.getMsgId();
        log.warn("消息下发失败: msgId={}, error={}", msgId, errorMessage);

        // Update status SENDING → FAILED
        messageMapper.updateStatus(msgId, MessageStatus.SENDING.name(), MessageStatus.FAILED.name());

        // Increment retry times
        messageMapper.incrementRetryTimes(msgId);

        int currentRetry = msg.getRetryTimes() != null ? msg.getRetryTimes() : 0;
        int maxRetry = msg.getMaxRetryTimes() != null ? msg.getMaxRetryTimes() : 0;

        if (currentRetry < maxRetry) {
            // Can retry: update FAILED → RETRYING, submit to retry queue
            messageMapper.updateStatus(msgId, MessageStatus.FAILED.name(), MessageStatus.RETRYING.name());
            submitRetry(msgId);
            log.info("消息进入重试队列: msgId={}, retryTimes={}/{}", msgId, currentRetry + 1, maxRetry);
        } else {
            // Max retries reached: submit to dead letter queue
            submitDeadLetter(msgId, errorMessage);
            log.info("消息进入死信队列: msgId={}, retryTimes={}/{}", msgId, currentRetry, maxRetry);
        }
    }

    private void submitRetry(String msgId) {
        try {
            rocketMQTemplate.syncSend(TOPIC_MSG_RETRY,
                    MessageBuilder.withPayload(msgId).build());
        } catch (Exception e) {
            log.error("投递重试队列失败: msgId={}", msgId, e);
        }
    }

    private void submitDeadLetter(String msgId, String reason) {
        try {
            rocketMQTemplate.syncSend(TOPIC_MSG_DEAD_LETTER,
                    MessageBuilder.withPayload(msgId)
                            .setHeader("REASON", reason)
                            .build());
        } catch (Exception e) {
            log.error("投递死信队列失败: msgId={}", msgId, e);
        }
        messageMapper.updateStatus(msgId, MessageStatus.FAILED.name(), MessageStatus.DEAD_LETTER.name());
    }

    private boolean isTimeoutException(Exception e) {
        return e instanceof TimeoutException
                || e instanceof SocketTimeoutException
                || (e.getCause() != null && (e.getCause() instanceof TimeoutException
                        || e.getCause() instanceof SocketTimeoutException));
    }
}
