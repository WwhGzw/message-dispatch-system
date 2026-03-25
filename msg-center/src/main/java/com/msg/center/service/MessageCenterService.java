package com.msg.center.service;

import cn.hutool.core.util.IdUtil;
import com.msg.center.exception.TemplateRenderException;
import com.msg.center.model.RouteResult;
import com.msg.center.statemachine.MessageStateMachine;
import com.msg.common.dto.*;

import com.msg.common.entity.MessageEntity;
import com.msg.common.enums.MessageStatus;
import com.msg.common.mapper.MessageMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 消息中心核心服务
 * <p>
 * 即时消息下发主流程：
 * 幂等检查 → 雪花算法生成 msgId → 模板渲染 → 消息路由 → 持久化(PENDING) → RocketMQ 投递 → 状态更新(SENDING)
 */
@Service
public class MessageCenterService {

    private static final Logger log = LoggerFactory.getLogger(MessageCenterService.class);

    public static final String TOPIC_MSG_SEND = "MSG_SEND";
    private static final int DEFAULT_MAX_RETRY_TIMES = 10;

    private final IdempotentService idempotentService;
    private final TemplateRenderService templateRenderService;
    private final MessageRouter messageRouter;
    private final MessageStateMachine stateMachine;
    private final MessageMapper messageMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final RetryHandler retryHandler;

    public MessageCenterService(IdempotentService idempotentService,
                                TemplateRenderService templateRenderService,
                                MessageRouter messageRouter,
                                MessageStateMachine stateMachine,
                                MessageMapper messageMapper,
                                RocketMQTemplate rocketMQTemplate,
                                RetryHandler retryHandler) {
        this.idempotentService = idempotentService;
        this.templateRenderService = templateRenderService;
        this.messageRouter = messageRouter;
        this.stateMachine = stateMachine;
        this.messageMapper = messageMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.retryHandler = retryHandler;
    }

    /**
     * 即时消息下发
     * <p>
     * 流程：
     * 1. 幂等检查（Redis 分布式锁 + DB 二次校验）
     * 2. 雪花算法生成全局唯一 msgId
     * 3. 模板渲染
     * 4. 消息路由（黑白名单/灰度/渠道选择）
     * 5. 持久化消息记录（status=PENDING）
     * 6. RocketMQ 投递
     * 7. 状态更新（PENDING→SENDING）
     * 8. 释放幂等锁
     *
     * @param request 下发请求
     * @return 下发结果
     */
    public SendResult processSendNow(SendRequest request) {
        String bizType = request.getBizType();
        String bizId = request.getBizId();
        String channel = request.getChannel();

        try {
            // Step 1: 幂等检查
            IdempotentResult idempotentResult = idempotentService.checkAndLock(bizType, bizId, channel);
            if (idempotentResult.isDuplicate()) {
                return SendResult.idempotent(idempotentResult.getExistingMsgId());
            }

            try {
                // Step 2: 生成全局唯一 msgId（雪花算法）
                String msgId = IdUtil.getSnowflakeNextIdStr();

                // Step 3: 模板渲染
                String content;
                try {
                    content = templateRenderService.renderTemplate(
                            request.getTemplateCode(), request.getVariables());
                } catch (TemplateRenderException e) {
                    log.error("模板渲染失败, bizType={}, bizId={}, templateCode={}",
                            bizType, bizId, request.getTemplateCode(), e);
                    return SendResult.fail("模板渲染失败: " + e.getMessage());
                }

                // Step 4: 消息路由
                RouteResult routeResult = messageRouter.route(request.getReceiver(), channel);
                if (routeResult.isBlocked()) {
                    return SendResult.blocked(routeResult.getReason());
                }

                // Step 5: 持久化消息记录（status=PENDING）
                MessageEntity entity = buildMessageEntity(msgId, request, content);
                try {
                    messageMapper.insert(entity);
                } catch (DuplicateKeyException e) {
                    log.warn("DB 唯一索引冲突，幂等兜底. bizType={}, bizId={}, channel={}",
                            bizType, bizId, channel);
                    IdempotentResult dupResult = idempotentService.handleDuplicateKey(bizType, bizId, channel);
                    if (dupResult.isDuplicate()) {
                        return SendResult.idempotent(dupResult.getExistingMsgId());
                    }
                    return SendResult.fail("消息插入冲突");
                }

                // Step 6: RocketMQ 投递
                try {
                    rocketMQTemplate.syncSend(TOPIC_MSG_SEND,
                            MessageBuilder.withPayload(msgId).build());
                } catch (Exception e) {
                    // MQ 投递失败 → 保持 PENDING 状态，返回失败
                    log.error("RocketMQ 投递失败, msgId={}", msgId, e);
                    return SendResult.fail("消息投递失败: " + e.getMessage());
                }

                // Step 7: 状态更新 PENDING → SENDING
                stateMachine.transitStatus(msgId, MessageStatus.PENDING, MessageStatus.SENDING);

                // Step 8: 返回成功
                return SendResult.success(msgId);

            } finally {
                // 释放幂等锁
                idempotentService.releaseLock(bizType, bizId, channel);
            }

        } catch (Exception e) {
            log.error("消息下发异常, bizType={}, bizId={}, channel={}", bizType, bizId, channel, e);
            return SendResult.fail("系统异常: " + e.getMessage());
        }
    }

    /** RocketMQ 最大延迟级别对应 2 小时 = 7200 秒 */
    static final long MAX_SHORT_DELAY_SECONDS = 7200;

    /**
     * 延迟/定时消息下发
     * <p>
     * 流程：
     * 1. 校验 sendTime 合法性（必须为未来时间）
     * 2. 幂等检查
     * 3. 模板渲染 + 消息路由
     * 4. 计算延迟时长 = sendTime - now
     * 5a. 短延迟（≤ 2h）：持久化 + RocketMQ 延迟消息投递 + 状态更新 SENDING
     * 5b. 长延迟（> 2h）：持久化到 DB（status=PENDING, sendTime），由 XXL-Job 扫描投递
     *
     * @param request 延迟下发请求
     * @return 下发结果
     */
    public SendResult processSendDelay(DelaySendRequest request) {
        String bizType = request.getBizType();
        String bizId = request.getBizId();
        String channel = request.getChannel();

        // Step 1: 校验 sendTime 合法性
        LocalDateTime now = LocalDateTime.now();
        if (request.getSendTime() == null || !request.getSendTime().isAfter(now)) {
            return SendResult.fail("sendTime必须为未来时间");
        }

        try {
            // Step 2: 幂等检查
            IdempotentResult idempotentResult = idempotentService.checkAndLock(bizType, bizId, channel);
            if (idempotentResult.isDuplicate()) {
                return SendResult.idempotent(idempotentResult.getExistingMsgId());
            }

            try {
                // Step 3: 生成 msgId
                String msgId = IdUtil.getSnowflakeNextIdStr();

                // Step 4: 模板渲染
                String content;
                try {
                    content = templateRenderService.renderTemplate(
                            request.getTemplateCode(), request.getVariables());
                } catch (TemplateRenderException e) {
                    log.error("模板渲染失败, bizType={}, bizId={}, templateCode={}",
                            bizType, bizId, request.getTemplateCode(), e);
                    return SendResult.fail("模板渲染失败: " + e.getMessage());
                }

                // Step 5: 消息路由
                RouteResult routeResult = messageRouter.route(request.getReceiver(), channel);
                if (routeResult.isBlocked()) {
                    return SendResult.blocked(routeResult.getReason());
                }

                // Step 6: 计算延迟
                long delaySeconds = Duration.between(now, request.getSendTime()).getSeconds();

                // Step 7: 构建并持久化消息实体
                MessageEntity entity = buildDelayMessageEntity(msgId, request, content);
                try {
                    messageMapper.insert(entity);
                } catch (DuplicateKeyException e) {
                    log.warn("DB 唯一索引冲突，幂等兜底. bizType={}, bizId={}, channel={}",
                            bizType, bizId, channel);
                    IdempotentResult dupResult = idempotentService.handleDuplicateKey(bizType, bizId, channel);
                    if (dupResult.isDuplicate()) {
                        return SendResult.idempotent(dupResult.getExistingMsgId());
                    }
                    return SendResult.fail("消息插入冲突");
                }

                if (delaySeconds <= MAX_SHORT_DELAY_SECONDS) {
                    // 短延迟：RocketMQ 延迟消息
                    int delayLevel = RetryHandler.mapSecondsToDelayLevel(delaySeconds);
                    try {
                        rocketMQTemplate.syncSend(TOPIC_MSG_SEND,
                                MessageBuilder.withPayload(msgId).build(), 3000, delayLevel);
                    } catch (Exception e) {
                        log.error("RocketMQ 延迟消息投递失败, msgId={}", msgId, e);
                        return SendResult.fail("消息投递失败: " + e.getMessage());
                    }
                    // 状态更新 PENDING → SENDING
                    stateMachine.transitStatus(msgId, MessageStatus.PENDING, MessageStatus.SENDING);
                } else {
                    // 长延迟：仅持久化，由 XXL-Job 定时扫描投递
                    log.info("长延迟消息已持久化，等待定时任务扫描: msgId={}, sendTime={}",
                            msgId, request.getSendTime());
                }

                return SendResult.success(msgId);

            } finally {
                idempotentService.releaseLock(bizType, bizId, channel);
            }

        } catch (Exception e) {
            log.error("延迟消息下发异常, bizType={}, bizId={}, channel={}", bizType, bizId, channel, e);
            return SendResult.fail("系统异常: " + e.getMessage());
        }
    }

    /**
     * 构建延迟消息实体
     */
    private MessageEntity buildDelayMessageEntity(String msgId, DelaySendRequest request, String content) {
        LocalDateTime now = LocalDateTime.now();
        return MessageEntity.builder()
                .msgId(msgId)
                .bizType(request.getBizType())
                .bizId(request.getBizId())
                .channel(request.getChannel())
                .templateCode(request.getTemplateCode())
                .content(content)
                .receiver(request.getReceiver())
                .status(MessageStatus.PENDING.name())
                .retryTimes(0)
                .maxRetryTimes(DEFAULT_MAX_RETRY_TIMES)
                .sendTime(request.getSendTime())
                .priority(request.getPriority())
                .createTime(now)
                .updateTime(now)
                .build();
    }

    /**
     * 消息撤回
     * <p>
     * 仅 PENDING 或 SENDING 状态的消息可撤回，调用状态机转换为 CANCELLED。
     * 终态消息拒绝撤回并返回失败原因。
     *
     * @param request 撤回请求
     * @return 撤回结果
     */
    public CancelResult cancelMessage(CancelRequest request) {
        String msgId = request.getMsgId();

        // Step 1: 查询消息
        MessageEntity entity = messageMapper.selectByMsgId(msgId);
        if (entity == null) {
            return CancelResult.fail("消息不存在");
        }

        // Step 2: 检查当前状态
        MessageStatus currentStatus;
        try {
            currentStatus = MessageStatus.valueOf(entity.getStatus());
        } catch (IllegalArgumentException e) {
            return CancelResult.fail("消息状态异常: " + entity.getStatus());
        }

        // 终态拒绝撤回
        if (currentStatus == MessageStatus.SUCCESS
                || currentStatus == MessageStatus.DEAD_LETTER
                || currentStatus == MessageStatus.CANCELLED) {
            return CancelResult.fail("消息已处于终态: " + currentStatus);
        }

        // 非可撤回状态（FAILED/RETRYING）
        if (currentStatus != MessageStatus.PENDING && currentStatus != MessageStatus.SENDING) {
            return CancelResult.fail("消息状态不可撤回: " + currentStatus);
        }

        // Step 3: 调用状态机转换为 CANCELLED
        boolean transited = stateMachine.transitStatus(msgId, currentStatus, MessageStatus.CANCELLED);
        if (!transited) {
            return CancelResult.fail("撤回失败，状态已变更");
        }

        return CancelResult.success();
    }

    /**
     * 消息状态查询
     * <p>
     * 支持按 msgId 或 bizType+bizId 查询，返回当前状态、重试次数和实际发送时间。
     *
     * @param query 查询条件
     * @return 消息状态视图对象，未找到时返回 null
     */
    public MessageStatusVO queryStatus(StatusQuery query) {
        MessageEntity entity = null;

        if (query.getMsgId() != null && !query.getMsgId().isEmpty()) {
            entity = messageMapper.selectByMsgId(query.getMsgId());
        } else if (query.getBizType() != null && query.getBizId() != null) {
            entity = messageMapper.selectByBizTypeAndBizId(query.getBizType(), query.getBizId());
        }

        if (entity == null) {
            return null;
        }

        return MessageStatusVO.builder()
                .msgId(entity.getMsgId())
                .status(entity.getStatus())
                .retryTimes(entity.getRetryTimes())
                .actualSendTime(entity.getActualSendTime())
                .build();
    }

    /**
     * 构建消息实体
     */
    private MessageEntity buildMessageEntity(String msgId, SendRequest request, String content) {
        LocalDateTime now = LocalDateTime.now();
        return MessageEntity.builder()
                .msgId(msgId)
                .bizType(request.getBizType())
                .bizId(request.getBizId())
                .channel(request.getChannel())
                .templateCode(request.getTemplateCode())
                .content(content)
                .receiver(request.getReceiver())
                .status(MessageStatus.PENDING.name())
                .retryTimes(0)
                .maxRetryTimes(DEFAULT_MAX_RETRY_TIMES)
                .priority(request.getPriority())
                .createTime(now)
                .updateTime(now)
                .build();
    }
}
