package com.msg.executor.consumer;

import com.msg.common.entity.MessageEntity;
import com.msg.common.enums.ChannelType;
import com.msg.common.enums.MessageStatus;
import com.msg.common.mapper.MessageMapper;
import com.msg.executor.channel.ChannelSender;
import com.msg.executor.channel.SendChannelResult;
import com.msg.executor.channel.SendContext;
import com.msg.executor.engine.ChannelExecutor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;

import java.net.SocketTimeoutException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MessageConsumer 单元测试
 * 覆盖：成功下发、终态跳过、失败重试、失败死信、超时异常处理
 */
class MessageConsumerTest {

    private MessageMapper messageMapper;
    private ChannelExecutor channelExecutor;
    private RocketMQTemplate rocketMQTemplate;
    private ChannelSender smsSender;
    private MessageConsumer consumer;

    @BeforeEach
    void setUp() {
        messageMapper = mock(MessageMapper.class);
        channelExecutor = mock(ChannelExecutor.class);
        rocketMQTemplate = mock(RocketMQTemplate.class);
        smsSender = mock(ChannelSender.class);
        consumer = new MessageConsumer(messageMapper, channelExecutor, rocketMQTemplate);
    }

    private MessageEntity buildMessage(String msgId, String status, int retryTimes, int maxRetryTimes) {
        return MessageEntity.builder()
                .msgId(msgId)
                .bizType("ORDER")
                .bizId("BIZ001")
                .channel("SMS")
                .content("Hello")
                .receiver("138xxxx1234")
                .status(status)
                .retryTimes(retryTimes)
                .maxRetryTimes(maxRetryTimes)
                .extParams("{}")
                .build();
    }

    // ========== 成功下发 ==========

    @Test
    void onMessage_successfulDelivery_updatesStatusToSuccessAndRecordsTime() {
        MessageEntity msg = buildMessage("msg-001", "SENDING", 0, 3);
        when(messageMapper.selectByMsgId("msg-001")).thenReturn(msg);
        when(channelExecutor.getSender(ChannelType.SMS)).thenReturn(smsSender);
        when(smsSender.send(any(SendContext.class)))
                .thenReturn(SendChannelResult.success("ch-001"));

        consumer.onMessage("msg-001");

        verify(messageMapper).updateStatus("msg-001", "SENDING", "SUCCESS");
        verify(messageMapper).updateActualSendTime(eq("msg-001"), any(LocalDateTime.class));
        verifyNoMoreInteractions(rocketMQTemplate);
    }

    // ========== 消息不存在 ==========

    @Test
    void onMessage_messageNotFound_logsWarningAndReturns() {
        when(messageMapper.selectByMsgId("msg-not-exist")).thenReturn(null);

        consumer.onMessage("msg-not-exist");

        verify(messageMapper).selectByMsgId("msg-not-exist");
        verifyNoMoreInteractions(messageMapper);
        verifyNoInteractions(channelExecutor);
    }

    // ========== 终态跳过 ==========

    @Test
    void onMessage_terminalStateSuccess_skipsProcessing() {
        MessageEntity msg = buildMessage("msg-002", "SUCCESS", 0, 3);
        when(messageMapper.selectByMsgId("msg-002")).thenReturn(msg);

        consumer.onMessage("msg-002");

        verify(messageMapper).selectByMsgId("msg-002");
        verifyNoMoreInteractions(messageMapper);
        verifyNoInteractions(channelExecutor);
    }

    @Test
    void onMessage_terminalStateCancelled_skipsProcessing() {
        MessageEntity msg = buildMessage("msg-003", "CANCELLED", 0, 3);
        when(messageMapper.selectByMsgId("msg-003")).thenReturn(msg);

        consumer.onMessage("msg-003");

        verify(messageMapper).selectByMsgId("msg-003");
        verifyNoMoreInteractions(messageMapper);
        verifyNoInteractions(channelExecutor);
    }

    @Test
    void onMessage_terminalStateDeadLetter_skipsProcessing() {
        MessageEntity msg = buildMessage("msg-004", "DEAD_LETTER", 3, 3);
        when(messageMapper.selectByMsgId("msg-004")).thenReturn(msg);

        consumer.onMessage("msg-004");

        verify(messageMapper).selectByMsgId("msg-004");
        verifyNoMoreInteractions(messageMapper);
        verifyNoInteractions(channelExecutor);
    }

    // ========== 失败 + 重试 ==========

    @Test
    void onMessage_sendFailure_withRetryAvailable_entersRetryFlow() {
        MessageEntity msg = buildMessage("msg-005", "SENDING", 1, 3);
        when(messageMapper.selectByMsgId("msg-005")).thenReturn(msg);
        when(channelExecutor.getSender(ChannelType.SMS)).thenReturn(smsSender);
        when(smsSender.send(any(SendContext.class)))
                .thenReturn(SendChannelResult.fail("渠道下发失败"));

        consumer.onMessage("msg-005");

        // Verify: SENDING → FAILED
        verify(messageMapper).updateStatus("msg-005", "SENDING", "FAILED");
        // Verify: increment retry times
        verify(messageMapper).incrementRetryTimes("msg-005");
        // Verify: FAILED → RETRYING
        verify(messageMapper).updateStatus("msg-005", "FAILED", "RETRYING");
        // Verify: submitted to retry queue
        verify(rocketMQTemplate).syncSend(eq("MSG_RETRY"), any(Message.class));
        // Verify: NOT submitted to dead letter
        verify(rocketMQTemplate, never()).syncSend(eq("MSG_DEAD_LETTER"), any(Message.class));
    }

    // ========== 失败 + 死信 ==========

    @Test
    void onMessage_sendFailure_maxRetriesReached_entersDeadLetterFlow() {
        MessageEntity msg = buildMessage("msg-006", "SENDING", 3, 3);
        when(messageMapper.selectByMsgId("msg-006")).thenReturn(msg);
        when(channelExecutor.getSender(ChannelType.SMS)).thenReturn(smsSender);
        when(smsSender.send(any(SendContext.class)))
                .thenReturn(SendChannelResult.fail("渠道下发失败"));

        consumer.onMessage("msg-006");

        // Verify: SENDING → FAILED
        verify(messageMapper).updateStatus("msg-006", "SENDING", "FAILED");
        // Verify: increment retry times
        verify(messageMapper).incrementRetryTimes("msg-006");
        // Verify: submitted to dead letter queue
        verify(rocketMQTemplate).syncSend(eq("MSG_DEAD_LETTER"), any(Message.class));
        // Verify: FAILED → DEAD_LETTER
        verify(messageMapper).updateStatus("msg-006", "FAILED", "DEAD_LETTER");
        // Verify: NOT submitted to retry queue
        verify(rocketMQTemplate, never()).syncSend(eq("MSG_RETRY"), any(Message.class));
    }

    // ========== 超时异常 ==========

    @Test
    void onMessage_timeoutException_entersRetryFlow() {
        MessageEntity msg = buildMessage("msg-007", "SENDING", 0, 3);
        when(messageMapper.selectByMsgId("msg-007")).thenReturn(msg);
        when(channelExecutor.getSender(ChannelType.SMS)).thenReturn(smsSender);
        when(smsSender.send(any(SendContext.class)))
                .thenThrow(new RuntimeException(new SocketTimeoutException("Read timed out")));

        consumer.onMessage("msg-007");

        // Verify failure handling triggered
        verify(messageMapper).updateStatus("msg-007", "SENDING", "FAILED");
        verify(messageMapper).incrementRetryTimes("msg-007");
        // retryTimes(0) < maxRetryTimes(3) → retry
        verify(messageMapper).updateStatus("msg-007", "FAILED", "RETRYING");
        verify(rocketMQTemplate).syncSend(eq("MSG_RETRY"), any(Message.class));
    }

    @Test
    void onMessage_genericException_entersRetryFlow() {
        MessageEntity msg = buildMessage("msg-008", "SENDING", 0, 3);
        when(messageMapper.selectByMsgId("msg-008")).thenReturn(msg);
        when(channelExecutor.getSender(ChannelType.SMS)).thenReturn(smsSender);
        when(smsSender.send(any(SendContext.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        consumer.onMessage("msg-008");

        verify(messageMapper).updateStatus("msg-008", "SENDING", "FAILED");
        verify(messageMapper).incrementRetryTimes("msg-008");
        verify(messageMapper).updateStatus("msg-008", "FAILED", "RETRYING");
        verify(rocketMQTemplate).syncSend(eq("MSG_RETRY"), any(Message.class));
    }

    // ========== SendContext 构建验证 ==========

    @Test
    void onMessage_buildsSendContextCorrectly() {
        MessageEntity msg = buildMessage("msg-009", "SENDING", 0, 3);
        msg.setExtParams("{\"apiKey\":\"test\"}");
        when(messageMapper.selectByMsgId("msg-009")).thenReturn(msg);
        when(channelExecutor.getSender(ChannelType.SMS)).thenReturn(smsSender);
        when(smsSender.send(any(SendContext.class)))
                .thenReturn(SendChannelResult.success("ch-009"));

        consumer.onMessage("msg-009");

        ArgumentCaptor<SendContext> captor = ArgumentCaptor.forClass(SendContext.class);
        verify(smsSender).send(captor.capture());
        SendContext ctx = captor.getValue();
        assertEquals("msg-009", ctx.getMsgId());
        assertEquals("SMS", ctx.getChannel());
        assertEquals("Hello", ctx.getContent());
        assertEquals("138xxxx1234", ctx.getReceiver());
        assertEquals("{\"apiKey\":\"test\"}", ctx.getChannelConfig());
    }
}
