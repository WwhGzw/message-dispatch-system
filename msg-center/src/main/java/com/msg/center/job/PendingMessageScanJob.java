package com.msg.center.job;

import com.msg.center.statemachine.MessageStateMachine;
import com.msg.common.entity.MessageEntity;
import com.msg.common.enums.MessageStatus;
import com.msg.common.mapper.MessageMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时扫描到期 PENDING 消息并投递到 MQ 的任务。
 * <p>
 * 用于长延迟/精确定时消息场景：消息持久化到 DB 后保持 PENDING 状态，
 * 由本定时任务在 send_time 到期后扫描并投递到 RocketMQ 主队列进行消费。
 * <p>
 * 需求: 2.3, 2.4
 */
@Component
public class PendingMessageScanJob {

    private static final Logger log = LoggerFactory.getLogger(PendingMessageScanJob.class);

    public static final String TOPIC_MSG_SEND = "MSG_SEND";

    private final MessageMapper messageMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final MessageStateMachine stateMachine;

    @Value("${msg.job.batch-size:100}")
    private int batchSize;

    public PendingMessageScanJob(MessageMapper messageMapper,
                                 RocketMQTemplate rocketMQTemplate,
                                 MessageStateMachine stateMachine) {
        this.messageMapper = messageMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.stateMachine = stateMachine;
    }

    /**
     * 定时扫描到期 PENDING 消息并投递到 MQ。
     * 扫描间隔通过 msg.job.scan-interval 配置，默认 60 秒。
     */
    @Scheduled(fixedDelayString = "${msg.job.scan-interval:60000}")
    public void scanAndDeliverExpiredMessages() {
        LocalDateTime now = LocalDateTime.now();
        List<MessageEntity> pendingMessages = messageMapper.selectPendingExpiredMessages(now, batchSize);

        if (pendingMessages.isEmpty()) {
            return;
        }

        log.info("扫描到 {} 条到期 PENDING 消息，开始投递", pendingMessages.size());

        int successCount = 0;
        int failCount = 0;

        for (MessageEntity msg : pendingMessages) {
            try {
                // 投递到 RocketMQ 主队列
                rocketMQTemplate.syncSend(TOPIC_MSG_SEND,
                        MessageBuilder.withPayload(msg.getMsgId()).build());

                // 状态更新 PENDING → SENDING
                boolean transitioned = stateMachine.transitStatus(
                        msg.getMsgId(), MessageStatus.PENDING, MessageStatus.SENDING);

                if (transitioned) {
                    successCount++;
                } else {
                    log.warn("消息状态更新失败（可能已被其他流程处理）: msgId={}", msg.getMsgId());
                    failCount++;
                }
            } catch (Exception e) {
                log.error("消息投递失败: msgId={}", msg.getMsgId(), e);
                failCount++;
            }
        }

        log.info("批次处理完成: 成功={}, 失败={}, 总数={}", successCount, failCount, pendingMessages.size());
    }

    // Visible for testing
    void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
