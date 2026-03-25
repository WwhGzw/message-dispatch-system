package com.msg.executor.engine;

import com.msg.common.enums.ChannelType;
import com.msg.executor.channel.ChannelSender;
import com.msg.executor.channel.SendChannelResult;
import com.msg.executor.channel.SendContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ChannelExecutor 单元测试
 * 覆盖：成功路由、无对应发送器→死信、未知渠道类型→死信
 */
class ChannelExecutorTest {

    private ChannelSender smsSender;
    private ChannelSender emailSender;
    private ChannelExecutor.DeadLetterHandler deadLetterHandler;
    private ChannelExecutor executor;

    @BeforeEach
    void setUp() {
        smsSender = mock(ChannelSender.class);
        when(smsSender.getChannelType()).thenReturn(ChannelType.SMS);

        emailSender = mock(ChannelSender.class);
        when(emailSender.getChannelType()).thenReturn(ChannelType.EMAIL);

        deadLetterHandler = mock(ChannelExecutor.DeadLetterHandler.class);

        executor = new ChannelExecutor(Arrays.asList(smsSender, emailSender));
        executor.init();
        executor.setDeadLetterHandler(deadLetterHandler);
    }

    // ========== getSender ==========

    @Test
    void getSender_registeredType_returnsSender() {
        assertSame(smsSender, executor.getSender(ChannelType.SMS));
        assertSame(emailSender, executor.getSender(ChannelType.EMAIL));
    }

    @Test
    void getSender_unregisteredType_returnsNull() {
        assertNull(executor.getSender(ChannelType.APP_PUSH));
        assertNull(executor.getSender(ChannelType.WEBHOOK));
    }

    // ========== execute: 成功路由 ==========

    @Test
    void execute_matchingSender_routesAndReturnsResult() {
        SendChannelResult expected = SendChannelResult.success("ch-msg-001");
        when(smsSender.send(any(SendContext.class))).thenReturn(expected);

        SendChannelResult result = executor.execute(
                "msg-001", "SMS", "Hello", "138xxxx1234", "{}");

        assertTrue(result.isSuccess());
        assertEquals("ch-msg-001", result.getChannelMsgId());
        verify(smsSender).send(any(SendContext.class));
        verifyNoInteractions(deadLetterHandler);
    }

    @Test
    void execute_passesCorrectContext() {
        when(smsSender.send(any(SendContext.class))).thenReturn(SendChannelResult.success("ok"));

        executor.execute("msg-002", "SMS", "content", "receiver", "{\"key\":\"val\"}");

        verify(smsSender).send(argThat(ctx ->
                "msg-002".equals(ctx.getMsgId()) &&
                "SMS".equals(ctx.getChannel()) &&
                "content".equals(ctx.getContent()) &&
                "receiver".equals(ctx.getReceiver()) &&
                "{\"key\":\"val\"}".equals(ctx.getChannelConfig())
        ));
    }

    @Test
    void execute_emailChannel_routesToEmailSender() {
        when(emailSender.send(any(SendContext.class)))
                .thenReturn(SendChannelResult.success("email-001"));

        SendChannelResult result = executor.execute(
                "msg-003", "EMAIL", "Hi", "test@example.com", "{}");

        assertTrue(result.isSuccess());
        assertEquals("email-001", result.getChannelMsgId());
        verify(emailSender).send(any(SendContext.class));
        verifyNoInteractions(smsSender);
    }

    // ========== execute: 无对应发送器 → 死信 ==========

    @Test
    void execute_noSenderForChannel_submitsToDeadLetterAndReturnsFail() {
        SendChannelResult result = executor.execute(
                "msg-004", "APP_PUSH", "Push msg", "device-token", "{}");

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("渠道发送器不存在"));
        verify(deadLetterHandler).submitDeadLetter(eq("msg-004"), contains("渠道发送器不存在"));
    }

    // ========== execute: 未知渠道类型 → 死信 ==========

    @Test
    void execute_unknownChannelType_submitsToDeadLetterAndReturnsFail() {
        SendChannelResult result = executor.execute(
                "msg-005", "UNKNOWN_CHANNEL", "content", "receiver", "{}");

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("未知渠道类型"));
        verify(deadLetterHandler).submitDeadLetter(eq("msg-005"), contains("未知渠道类型"));
    }

    // ========== execute: 无死信处理器时不抛异常 ==========

    @Test
    void execute_noDeadLetterHandler_doesNotThrow() {
        executor.setDeadLetterHandler(null);

        SendChannelResult result = executor.execute(
                "msg-006", "WEBHOOK", "payload", "http://hook.url", "{}");

        assertFalse(result.isSuccess());
        // Should not throw even without dead letter handler
    }

    // ========== 空发送器列表 ==========

    @Test
    void constructor_emptySenderList_initializesWithEmptyMap() {
        ChannelExecutor emptyExecutor = new ChannelExecutor(Collections.emptyList());
        emptyExecutor.init();

        assertNull(emptyExecutor.getSender(ChannelType.SMS));
    }

    @Test
    void constructor_nullSenderList_initializesWithEmptyMap() {
        ChannelExecutor nullExecutor = new ChannelExecutor(null);
        nullExecutor.init();

        assertNull(nullExecutor.getSender(ChannelType.SMS));
    }

    // ========== Spring 自动注入：新渠道注册后自动纳入路由 ==========

    @Test
    void init_newSenderRegistered_automaticallyAvailableForRouting() {
        ChannelSender webhookSender = mock(ChannelSender.class);
        when(webhookSender.getChannelType()).thenReturn(ChannelType.WEBHOOK);
        when(webhookSender.send(any(SendContext.class)))
                .thenReturn(SendChannelResult.success("wh-001"));

        ChannelExecutor fullExecutor = new ChannelExecutor(
                Arrays.asList(smsSender, emailSender, webhookSender));
        fullExecutor.init();

        // WEBHOOK sender should be automatically available
        assertSame(webhookSender, fullExecutor.getSender(ChannelType.WEBHOOK));

        SendChannelResult result = fullExecutor.execute(
                "msg-007", "WEBHOOK", "payload", "http://hook.url", "{}");
        assertTrue(result.isSuccess());
    }
}
