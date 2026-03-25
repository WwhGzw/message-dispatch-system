package com.msg.center.service;

import com.msg.center.exception.TemplateRenderException;
import com.msg.center.model.RouteResult;
import com.msg.center.statemachine.MessageStateMachine;
import com.msg.common.dto.*;

import com.msg.common.entity.ChannelConfigEntity;
import com.msg.common.entity.MessageEntity;
import com.msg.common.enums.MessageStatus;
import com.msg.common.mapper.MessageMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.Message;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MessageCenterService 单元测试
 * 覆盖: 成功下发、幂等重复、路由拦截、MQ 投递失败、DuplicateKeyException 处理、模板渲染失败
 */
@ExtendWith(MockitoExtension.class)
class MessageCenterServiceTest {

    @Mock
    private IdempotentService idempotentService;
    @Mock
    private TemplateRenderService templateRenderService;
    @Mock
    private MessageRouter messageRouter;
    @Mock
    private MessageStateMachine stateMachine;
    @Mock
    private MessageMapper messageMapper;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private RetryHandler retryHandler;

    private MessageCenterService messageCenterService;

    private SendRequest defaultRequest;

    @BeforeEach
    void setUp() {
        messageCenterService = new MessageCenterService(
                idempotentService, templateRenderService, messageRouter,
                stateMachine, messageMapper, rocketMQTemplate, retryHandler);

        defaultRequest = SendRequest.builder()
                .bizType("ORDER_NOTIFY")
                .bizId("ORDER_001")
                .channel("SMS")
                .templateCode("order_shipped")
                .receiver("13800001234")
                .variables(Map.of("orderNo", "ORDER_001"))
                .priority(2)
                .build();
    }

    // ========== 成功下发 ==========

    @Test
    void processSendNow_success_returnsSuccessWithMsgId() {
        // 幂等检查通过
        when(idempotentService.checkAndLock("ORDER_NOTIFY", "ORDER_001", "SMS"))
                .thenReturn(IdempotentResult.pass());
        // 模板渲染成功
        when(templateRenderService.renderTemplate(eq("order_shipped"), anyMap()))
                .thenReturn("您的订单ORDER_001已发货");
        // 路由通过
        ChannelConfigEntity config = ChannelConfigEntity.builder()
                .channelCode("SMS_ALIYUN").channelType("SMS").enabled(true).priority(1).build();
        when(messageRouter.route("13800001234", "SMS"))
                .thenReturn(RouteResult.success(config));
        // DB 插入成功
        when(messageMapper.insert(any(MessageEntity.class))).thenReturn(1);
        // MQ 投递成功
        when(rocketMQTemplate.syncSend(eq(MessageCenterService.TOPIC_MSG_SEND), any(Message.class)))
                .thenReturn(null);
        // 状态流转成功
        when(stateMachine.transitStatus(anyString(), eq(MessageStatus.PENDING), eq(MessageStatus.SENDING)))
                .thenReturn(true);

        SendResult result = messageCenterService.processSendNow(defaultRequest);

        assertTrue(result.isSuccess());
        assertNotNull(result.getMsgId());
        assertEquals("ACCEPTED", result.getStatus());

        // 验证调用顺序
        verify(idempotentService).checkAndLock("ORDER_NOTIFY", "ORDER_001", "SMS");
        verify(templateRenderService).renderTemplate(eq("order_shipped"), anyMap());
        verify(messageRouter).route("13800001234", "SMS");
        verify(messageMapper).insert(any(MessageEntity.class));
        verify(rocketMQTemplate).syncSend(eq(MessageCenterService.TOPIC_MSG_SEND), any(Message.class));
        verify(stateMachine).transitStatus(anyString(), eq(MessageStatus.PENDING), eq(MessageStatus.SENDING));
        verify(idempotentService).releaseLock("ORDER_NOTIFY", "ORDER_001", "SMS");
    }

    // ========== 幂等重复 ==========

    @Test
    void processSendNow_idempotentDuplicate_returnsExistingMsgId() {
        when(idempotentService.checkAndLock("ORDER_NOTIFY", "ORDER_001", "SMS"))
                .thenReturn(IdempotentResult.duplicate("existing-msg-123"));

        SendResult result = messageCenterService.processSendNow(defaultRequest);

        assertTrue(result.isSuccess());
        assertEquals("existing-msg-123", result.getMsgId());
        assertEquals("DUPLICATE", result.getStatus());

        // 不应调用后续流程
        verifyNoInteractions(templateRenderService);
        verifyNoInteractions(messageRouter);
        verifyNoInteractions(messageMapper);
        verifyNoInteractions(rocketMQTemplate);
    }

    // ========== 路由拦截 ==========

    @Test
    void processSendNow_routeBlocked_returnsBlocked() {
        when(idempotentService.checkAndLock("ORDER_NOTIFY", "ORDER_001", "SMS"))
                .thenReturn(IdempotentResult.pass());
        when(templateRenderService.renderTemplate(eq("order_shipped"), anyMap()))
                .thenReturn("渲染内容");
        when(messageRouter.route("13800001234", "SMS"))
                .thenReturn(RouteResult.blocked("黑名单拦截: 13800001234"));

        SendResult result = messageCenterService.processSendNow(defaultRequest);

        assertFalse(result.isSuccess());
        assertEquals("BLOCKED", result.getStatus());
        assertTrue(result.getMessage().contains("黑名单拦截"));

        // 不应持久化或投递 MQ
        verify(messageMapper, never()).insert(any());
        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
        // 应释放锁
        verify(idempotentService).releaseLock("ORDER_NOTIFY", "ORDER_001", "SMS");
    }

    // ========== MQ 投递失败 ==========

    @Test
    void processSendNow_mqSendFails_keepsPendingAndReturnsFail() {
        when(idempotentService.checkAndLock("ORDER_NOTIFY", "ORDER_001", "SMS"))
                .thenReturn(IdempotentResult.pass());
        when(templateRenderService.renderTemplate(eq("order_shipped"), anyMap()))
                .thenReturn("渲染内容");
        ChannelConfigEntity config = ChannelConfigEntity.builder()
                .channelCode("SMS_ALIYUN").channelType("SMS").enabled(true).priority(1).build();
        when(messageRouter.route("13800001234", "SMS"))
                .thenReturn(RouteResult.success(config));
        when(messageMapper.insert(any(MessageEntity.class))).thenReturn(1);
        // MQ 投递失败
        when(rocketMQTemplate.syncSend(eq(MessageCenterService.TOPIC_MSG_SEND), any(Message.class)))
                .thenThrow(new RuntimeException("Broker not available"));

        SendResult result = messageCenterService.processSendNow(defaultRequest);

        assertFalse(result.isSuccess());
        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("消息投递失败"));

        // 不应更新状态到 SENDING
        verify(stateMachine, never()).transitStatus(anyString(), any(), any());
        // 应释放锁
        verify(idempotentService).releaseLock("ORDER_NOTIFY", "ORDER_001", "SMS");
    }

    // ========== DuplicateKeyException 处理 ==========

    @Test
    void processSendNow_duplicateKeyException_returnsIdempotentResult() {
        when(idempotentService.checkAndLock("ORDER_NOTIFY", "ORDER_001", "SMS"))
                .thenReturn(IdempotentResult.pass());
        when(templateRenderService.renderTemplate(eq("order_shipped"), anyMap()))
                .thenReturn("渲染内容");
        ChannelConfigEntity config = ChannelConfigEntity.builder()
                .channelCode("SMS_ALIYUN").channelType("SMS").enabled(true).priority(1).build();
        when(messageRouter.route("13800001234", "SMS"))
                .thenReturn(RouteResult.success(config));
        // DB 插入抛出唯一索引冲突
        when(messageMapper.insert(any(MessageEntity.class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));
        when(idempotentService.handleDuplicateKey("ORDER_NOTIFY", "ORDER_001", "SMS"))
                .thenReturn(IdempotentResult.duplicate("dup-msg-456"));

        SendResult result = messageCenterService.processSendNow(defaultRequest);

        assertTrue(result.isSuccess());
        assertEquals("dup-msg-456", result.getMsgId());
        assertEquals("DUPLICATE", result.getStatus());

        // 不应投递 MQ
        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
        // 应释放锁
        verify(idempotentService).releaseLock("ORDER_NOTIFY", "ORDER_001", "SMS");
    }

    // ========== 模板渲染失败 ==========

    @Test
    void processSendNow_templateRenderFails_returnsFail() {
        when(idempotentService.checkAndLock("ORDER_NOTIFY", "ORDER_001", "SMS"))
                .thenReturn(IdempotentResult.pass());
        when(templateRenderService.renderTemplate(eq("order_shipped"), anyMap()))
                .thenThrow(new TemplateRenderException("模板不存在: order_shipped"));

        SendResult result = messageCenterService.processSendNow(defaultRequest);

        assertFalse(result.isSuccess());
        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("模板渲染失败"));

        // 不应调用路由、持久化、MQ
        verify(messageRouter, never()).route(anyString(), anyString());
        verify(messageMapper, never()).insert(any());
        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
        // 应释放锁
        verify(idempotentService).releaseLock("ORDER_NOTIFY", "ORDER_001", "SMS");
    }

    // ========== 消息实体构建验证 ==========

    @Test
    void processSendNow_success_persistsEntityWithCorrectFields() {
        when(idempotentService.checkAndLock("ORDER_NOTIFY", "ORDER_001", "SMS"))
                .thenReturn(IdempotentResult.pass());
        when(templateRenderService.renderTemplate(eq("order_shipped"), anyMap()))
                .thenReturn("您的订单已发货");
        ChannelConfigEntity config = ChannelConfigEntity.builder()
                .channelCode("SMS_ALIYUN").channelType("SMS").enabled(true).priority(1).build();
        when(messageRouter.route("13800001234", "SMS"))
                .thenReturn(RouteResult.success(config));
        when(messageMapper.insert(any(MessageEntity.class))).thenReturn(1);
        when(rocketMQTemplate.syncSend(eq(MessageCenterService.TOPIC_MSG_SEND), any(Message.class)))
                .thenReturn(null);
        when(stateMachine.transitStatus(anyString(), eq(MessageStatus.PENDING), eq(MessageStatus.SENDING)))
                .thenReturn(true);

        messageCenterService.processSendNow(defaultRequest);

        verify(messageMapper).insert(argThat(entity -> {
            assertNotNull(entity.getMsgId());
            assertEquals("ORDER_NOTIFY", entity.getBizType());
            assertEquals("ORDER_001", entity.getBizId());
            assertEquals("SMS", entity.getChannel());
            assertEquals("order_shipped", entity.getTemplateCode());
            assertEquals("您的订单已发货", entity.getContent());
            assertEquals("13800001234", entity.getReceiver());
            assertEquals(MessageStatus.PENDING.name(), entity.getStatus());
            assertEquals(0, entity.getRetryTimes());
            assertEquals(10, entity.getMaxRetryTimes());
            assertEquals(2, entity.getPriority());
            assertNotNull(entity.getCreateTime());
            assertNotNull(entity.getUpdateTime());
            return true;
        }));
    }

    // ========== 延迟消息：短延迟（RocketMQ 延迟消息） ==========

    @Test
    void processSendDelay_shortDelay_sendsWithDelayLevel() {
        DelaySendRequest delayRequest = DelaySendRequest.builder()
                .bizType("PROMO").bizId("PROMO_001").channel("APP_PUSH")
                .templateCode("promo_push").receiver("device_token_123")
                .variables(Map.of("title", "促销"))
                .sendTime(LocalDateTime.now().plusMinutes(30))
                .build();

        when(idempotentService.checkAndLock("PROMO", "PROMO_001", "APP_PUSH"))
                .thenReturn(IdempotentResult.pass());
        when(templateRenderService.renderTemplate(eq("promo_push"), anyMap()))
                .thenReturn("促销内容");
        ChannelConfigEntity config = ChannelConfigEntity.builder()
                .channelCode("PUSH_FCM").channelType("APP_PUSH").enabled(true).priority(1).build();
        when(messageRouter.route("device_token_123", "APP_PUSH"))
                .thenReturn(RouteResult.success(config));
        when(messageMapper.insert(any(MessageEntity.class))).thenReturn(1);
        when(rocketMQTemplate.syncSend(eq(MessageCenterService.TOPIC_MSG_SEND), any(Message.class), eq(3000), anyInt()))
                .thenReturn(null);
        when(stateMachine.transitStatus(anyString(), eq(MessageStatus.PENDING), eq(MessageStatus.SENDING)))
                .thenReturn(true);

        SendResult result = messageCenterService.processSendDelay(delayRequest);

        assertTrue(result.isSuccess());
        assertNotNull(result.getMsgId());
        assertEquals("ACCEPTED", result.getStatus());

        // 验证使用了带 delayLevel 的 syncSend
        verify(rocketMQTemplate).syncSend(eq(MessageCenterService.TOPIC_MSG_SEND), any(Message.class), eq(3000), anyInt());
        verify(stateMachine).transitStatus(anyString(), eq(MessageStatus.PENDING), eq(MessageStatus.SENDING));
        verify(idempotentService).releaseLock("PROMO", "PROMO_001", "APP_PUSH");
    }

    // ========== 延迟消息：长延迟（DB 持久化，不投递 MQ） ==========

    @Test
    void processSendDelay_longDelay_persistsToDbOnly() {
        LocalDateTime futureTime = LocalDateTime.now().plusHours(5);
        DelaySendRequest delayRequest = DelaySendRequest.builder()
                .bizType("REPORT").bizId("REPORT_001").channel("EMAIL")
                .templateCode("report_email").receiver("user@example.com")
                .variables(Map.of("date", "2024-01-01"))
                .sendTime(futureTime)
                .build();

        when(idempotentService.checkAndLock("REPORT", "REPORT_001", "EMAIL"))
                .thenReturn(IdempotentResult.pass());
        when(templateRenderService.renderTemplate(eq("report_email"), anyMap()))
                .thenReturn("报告内容");
        ChannelConfigEntity config = ChannelConfigEntity.builder()
                .channelCode("EMAIL_SES").channelType("EMAIL").enabled(true).priority(1).build();
        when(messageRouter.route("user@example.com", "EMAIL"))
                .thenReturn(RouteResult.success(config));
        when(messageMapper.insert(any(MessageEntity.class))).thenReturn(1);

        SendResult result = messageCenterService.processSendDelay(delayRequest);

        assertTrue(result.isSuccess());
        assertNotNull(result.getMsgId());
        assertEquals("ACCEPTED", result.getStatus());

        // 不应投递 MQ（长延迟由 XXL-Job 扫描）
        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class), anyLong(), anyInt());
        // 不应更新状态到 SENDING（保持 PENDING）
        verify(stateMachine, never()).transitStatus(anyString(), any(), any());
        // 验证持久化实体包含 sendTime
        verify(messageMapper).insert(argThat(entity -> {
            assertEquals(MessageStatus.PENDING.name(), entity.getStatus());
            assertNotNull(entity.getSendTime());
            return true;
        }));
        verify(idempotentService).releaseLock("REPORT", "REPORT_001", "EMAIL");
    }

    // ========== 延迟消息：sendTime 为过去时间 ==========

    @Test
    void processSendDelay_pastSendTime_returnsFail() {
        DelaySendRequest delayRequest = DelaySendRequest.builder()
                .bizType("PROMO").bizId("PROMO_002").channel("SMS")
                .templateCode("promo_sms").receiver("13800001234")
                .sendTime(LocalDateTime.now().minusMinutes(10))
                .build();

        SendResult result = messageCenterService.processSendDelay(delayRequest);

        assertFalse(result.isSuccess());
        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getMessage().contains("sendTime必须为未来时间"));

        // 不应调用任何后续流程
        verifyNoInteractions(idempotentService);
        verifyNoInteractions(templateRenderService);
        verifyNoInteractions(messageRouter);
        verifyNoInteractions(messageMapper);
        verifyNoInteractions(rocketMQTemplate);
    }

    // ========== 延迟消息：幂等重复 ==========

    @Test
    void processSendDelay_idempotentDuplicate_returnsExistingMsgId() {
        DelaySendRequest delayRequest = DelaySendRequest.builder()
                .bizType("PROMO").bizId("PROMO_001").channel("APP_PUSH")
                .templateCode("promo_push").receiver("device_token_123")
                .sendTime(LocalDateTime.now().plusMinutes(30))
                .build();

        when(idempotentService.checkAndLock("PROMO", "PROMO_001", "APP_PUSH"))
                .thenReturn(IdempotentResult.duplicate("existing-delay-msg-789"));

        SendResult result = messageCenterService.processSendDelay(delayRequest);

        assertTrue(result.isSuccess());
        assertEquals("existing-delay-msg-789", result.getMsgId());
        assertEquals("DUPLICATE", result.getStatus());

        verifyNoInteractions(templateRenderService);
        verifyNoInteractions(messageRouter);
        verifyNoInteractions(messageMapper);
        verifyNoInteractions(rocketMQTemplate);
    }

    // ========== 消息撤回：成功撤回 PENDING 状态 ==========

    @Test
    void cancelMessage_pendingStatus_returnsSuccess() {
        MessageEntity entity = MessageEntity.builder()
                .msgId("msg-001").status(MessageStatus.PENDING.name()).build();
        when(messageMapper.selectByMsgId("msg-001")).thenReturn(entity);
        when(stateMachine.transitStatus("msg-001", MessageStatus.PENDING, MessageStatus.CANCELLED))
                .thenReturn(true);

        CancelResult result = messageCenterService.cancelMessage(
                CancelRequest.builder().msgId("msg-001").build());

        assertTrue(result.isSuccess());
        assertEquals("撤回成功", result.getMessage());
    }

    // ========== 消息撤回：成功撤回 SENDING 状态 ==========

    @Test
    void cancelMessage_sendingStatus_returnsSuccess() {
        MessageEntity entity = MessageEntity.builder()
                .msgId("msg-002").status(MessageStatus.SENDING.name()).build();
        when(messageMapper.selectByMsgId("msg-002")).thenReturn(entity);
        when(stateMachine.transitStatus("msg-002", MessageStatus.SENDING, MessageStatus.CANCELLED))
                .thenReturn(true);

        CancelResult result = messageCenterService.cancelMessage(
                CancelRequest.builder().msgId("msg-002").build());

        assertTrue(result.isSuccess());
    }

    // ========== 消息撤回：消息不存在 ==========

    @Test
    void cancelMessage_notFound_returnsFail() {
        when(messageMapper.selectByMsgId("msg-999")).thenReturn(null);

        CancelResult result = messageCenterService.cancelMessage(
                CancelRequest.builder().msgId("msg-999").build());

        assertFalse(result.isSuccess());
        assertEquals("消息不存在", result.getMessage());
    }

    // ========== 消息撤回：终态拒绝 ==========

    @Test
    void cancelMessage_terminalStatus_returnsFail() {
        MessageEntity entity = MessageEntity.builder()
                .msgId("msg-003").status(MessageStatus.SUCCESS.name()).build();
        when(messageMapper.selectByMsgId("msg-003")).thenReturn(entity);

        CancelResult result = messageCenterService.cancelMessage(
                CancelRequest.builder().msgId("msg-003").build());

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("消息已处于终态"));
    }

    // ========== 消息撤回：FAILED 状态不可撤回 ==========

    @Test
    void cancelMessage_failedStatus_returnsFail() {
        MessageEntity entity = MessageEntity.builder()
                .msgId("msg-004").status(MessageStatus.FAILED.name()).build();
        when(messageMapper.selectByMsgId("msg-004")).thenReturn(entity);

        CancelResult result = messageCenterService.cancelMessage(
                CancelRequest.builder().msgId("msg-004").build());

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("消息状态不可撤回"));
    }

    // ========== 消息撤回：乐观锁失败 ==========

    @Test
    void cancelMessage_optimisticLockFails_returnsFail() {
        MessageEntity entity = MessageEntity.builder()
                .msgId("msg-005").status(MessageStatus.PENDING.name()).build();
        when(messageMapper.selectByMsgId("msg-005")).thenReturn(entity);
        when(stateMachine.transitStatus("msg-005", MessageStatus.PENDING, MessageStatus.CANCELLED))
                .thenReturn(false);

        CancelResult result = messageCenterService.cancelMessage(
                CancelRequest.builder().msgId("msg-005").build());

        assertFalse(result.isSuccess());
        assertEquals("撤回失败，状态已变更", result.getMessage());
    }

    // ========== 状态查询：按 msgId 查询 ==========

    @Test
    void queryStatus_byMsgId_returnsStatus() {
        LocalDateTime sendTime = LocalDateTime.of(2024, 1, 1, 10, 30, 15);
        MessageEntity entity = MessageEntity.builder()
                .msgId("msg-100").status(MessageStatus.SUCCESS.name())
                .retryTimes(0).actualSendTime(sendTime).build();
        when(messageMapper.selectByMsgId("msg-100")).thenReturn(entity);

        MessageStatusVO vo = messageCenterService.queryStatus(
                StatusQuery.builder().msgId("msg-100").build());

        assertNotNull(vo);
        assertEquals("msg-100", vo.getMsgId());
        assertEquals("SUCCESS", vo.getStatus());
        assertEquals(0, vo.getRetryTimes());
        assertEquals(sendTime, vo.getActualSendTime());
    }

    // ========== 状态查询：按 bizType+bizId 查询 ==========

    @Test
    void queryStatus_byBizKey_returnsStatus() {
        MessageEntity entity = MessageEntity.builder()
                .msgId("msg-200").status(MessageStatus.SENDING.name())
                .retryTimes(2).actualSendTime(null).build();
        when(messageMapper.selectByBizTypeAndBizId("ORDER", "ORD_001")).thenReturn(entity);

        MessageStatusVO vo = messageCenterService.queryStatus(
                StatusQuery.builder().bizType("ORDER").bizId("ORD_001").build());

        assertNotNull(vo);
        assertEquals("msg-200", vo.getMsgId());
        assertEquals("SENDING", vo.getStatus());
        assertEquals(2, vo.getRetryTimes());
        assertNull(vo.getActualSendTime());
    }

    // ========== 状态查询：未找到返回 null ==========

    @Test
    void queryStatus_notFound_returnsNull() {
        when(messageMapper.selectByMsgId("msg-999")).thenReturn(null);

        MessageStatusVO vo = messageCenterService.queryStatus(
                StatusQuery.builder().msgId("msg-999").build());

        assertNull(vo);
    }
}
