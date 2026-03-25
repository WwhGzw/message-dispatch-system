package com.msg.center.service;

import com.msg.center.statemachine.MessageStateMachine;
import com.msg.common.enums.MessageStatus;
import com.msg.common.mapper.MessageMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * 重试与死信处理器
 * 管理消息重试策略（递增退避）与死信队列处理
 */
@Service
public class RetryHandler {

    private static final Logger log = LoggerFactory.getLogger(RetryHandler.class);

    public static final String TOPIC_MSG_RETRY = "MSG_RETRY";
    public static final String TOPIC_MSG_DEAD_LETTER = "MSG_DEAD_LETTER";

    /**
     * RocketMQ 固定延迟级别（18级）：
     * 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     * 对应级别:  1  2   3   4  5  6  7  8  9 10 11 12 13  14  15  16 17 18
     */
    private static final int[] DELAY_LEVEL_SECONDS = {
            1, 5, 10, 30, 60, 120, 180, 240, 300, 360, 420, 480, 540, 600, 1200, 1800, 3600, 7200
    };

    /**
     * 重试延迟表（秒）
     * 第1次: 10s, 第2次: 30s, 第3次: 1min, 第4次: 5min, 第5次: 30min,
     * 第6次: 1h, 第7次: 2h, 第8次: 6h, 第9次: 12h
     */
    static final long[] RETRY_DELAYS = {10, 30, 60, 300, 1800, 3600, 7200, 21600, 43200};

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private MessageStateMachine stateMachine;

    @Autowired
    private MessageMapper messageMapper;

    /**
     * 计算下次重试延迟（递增退避策略）
     *
     * @param retryTimes 当前重试次数（从1开始）
     * @return 延迟时间（秒），-1表示不再重试
     */
    public long calculateDelay(int retryTimes) {
        if (retryTimes < 1 || retryTimes > RETRY_DELAYS.length) {
            return -1;
        }
        return RETRY_DELAYS[retryTimes - 1];
    }

    /**
     * 投递到重试队列
     *
     * @param msgId      消息ID
     * @param retryTimes 当前重试次数（从1开始）
     * @return 是否投递成功
     */
    public boolean submitRetry(String msgId, int retryTimes) {
        long delaySec = calculateDelay(retryTimes);
        if (delaySec == -1) {
            log.info("重试次数已达上限，转入死信队列: msgId={}, retryTimes={}", msgId, retryTimes);
            submitDeadLetter(msgId, "达到最大重试次数: " + retryTimes);
            return false;
        }

        try {
            int delayLevel = mapSecondsToDelayLevel(delaySec);
            Message<String> message = MessageBuilder.withPayload(msgId)
                    .setHeader("DELAY", delayLevel)
                    .build();
            rocketMQTemplate.syncSend(TOPIC_MSG_RETRY, message, 3000, delayLevel);
            log.info("消息投递到重试队列成功: msgId={}, retryTimes={}, delaySec={}, delayLevel={}",
                    msgId, retryTimes, delaySec, delayLevel);
            return true;
        } catch (Exception e) {
            log.error("消息投递到重试队列失败: msgId={}, retryTimes={}", msgId, retryTimes, e);
            return false;
        }
    }

    /**
     * 投递到死信队列
     *
     * @param msgId  消息ID
     * @param reason 死信原因
     */
    public void submitDeadLetter(String msgId, String reason) {
        try {
            Message<String> message = MessageBuilder.withPayload(msgId)
                    .setHeader("REASON", reason)
                    .build();
            rocketMQTemplate.syncSend(TOPIC_MSG_DEAD_LETTER, message);
            log.info("消息投递到死信队列: msgId={}, reason={}", msgId, reason);
        } catch (Exception e) {
            log.error("消息投递到死信队列失败: msgId={}, reason={}", msgId, reason, e);
        }

        boolean transitioned = stateMachine.transitStatus(msgId, MessageStatus.FAILED, MessageStatus.DEAD_LETTER);
        if (transitioned) {
            log.info("消息状态更新为DEAD_LETTER: msgId={}", msgId);
        } else {
            log.warn("消息状态更新为DEAD_LETTER失败（可能已被其他流程处理）: msgId={}", msgId);
        }
    }

    /**
     * 将延迟秒数映射到 RocketMQ 延迟级别。
     * RocketMQ 固定延迟级别: 1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     * 选择大于等于目标秒数的最小延迟级别；若超出所有级别则返回最大级别。
     *
     * @param seconds 目标延迟秒数
     * @return RocketMQ 延迟级别（1-18）
     */
    public static int mapSecondsToDelayLevel(long seconds) {
        for (int i = 0; i < DELAY_LEVEL_SECONDS.length; i++) {
            if (DELAY_LEVEL_SECONDS[i] >= seconds) {
                return i + 1; // delay level is 1-based
            }
        }
        // 超出所有级别，返回最大级别 (2h)
        return DELAY_LEVEL_SECONDS.length;
    }
}
