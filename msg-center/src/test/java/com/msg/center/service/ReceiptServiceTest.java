package com.msg.center.service;

import com.msg.common.util.HmacSignatureUtil;
import com.msg.common.dto.ReceiptCallback;
import com.msg.center.statemachine.MessageStateMachine;
import com.msg.common.entity.MessageEntity;
import com.msg.common.entity.MessageReceiptEntity;
import com.msg.common.enums.MessageStatus;
import com.msg.common.mapper.MessageMapper;
import com.msg.common.mapper.MessageReceiptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReceiptService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    private static final String RECEIPT_SECRET = "test-receipt-secret";

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private MessageReceiptMapper messageReceiptMapper;

    @Mock
    private MessageStateMachine stateMachine;

    @InjectMocks
    private ReceiptService receiptService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(receiptService, "receiptSecret", RECEIPT_SECRET);
    }

    // ========== 签名验证成功 → 插入回执 → 更新状态 ==========

    @Test
    void processReceipt_validSignature_delivered_updatesStatusToSuccess() {
        ReceiptCallback callback = buildCallback("msg-001", "SMS", "ch-001", "DELIVERED");
        String signContent = receiptService.buildSignContent(callback);
        callback.setSignature(HmacSignatureUtil.generateSignature(RECEIPT_SECRET, signContent));

        MessageEntity message = buildMessage("msg-001", MessageStatus.SENDING);
        when(messageMapper.selectByMsgId("msg-001")).thenReturn(message);
        when(messageReceiptMapper.insert(any(MessageReceiptEntity.class))).thenReturn(1);
        when(stateMachine.transitStatus("msg-001", MessageStatus.SENDING, MessageStatus.SUCCESS)).thenReturn(true);

        receiptService.processReceipt(callback);

        // Verify receipt record inserted
        ArgumentCaptor<MessageReceiptEntity> captor = ArgumentCaptor.forClass(MessageReceiptEntity.class);
        verify(messageReceiptMapper).insert(captor.capture());
        MessageReceiptEntity receipt = captor.getValue();
        assertEquals("msg-001", receipt.getMsgId());
        assertEquals("SMS", receipt.getChannel());
        assertEquals("ch-001", receipt.getChannelMsgId());
        assertEquals("DELIVERED", receipt.getReceiptStatus());

        // Verify status updated to SUCCESS
        verify(stateMachine).transitStatus("msg-001", MessageStatus.SENDING, MessageStatus.SUCCESS);
    }

    @Test
    void processReceipt_validSignature_rejected_updatesStatusToFailed() {
        ReceiptCallback callback = buildCallback("msg-002", "EMAIL", "ch-002", "REJECTED");
        String signContent = receiptService.buildSignContent(callback);
        callback.setSignature(HmacSignatureUtil.generateSignature(RECEIPT_SECRET, signContent));

        MessageEntity message = buildMessage("msg-002", MessageStatus.SENDING);
        when(messageMapper.selectByMsgId("msg-002")).thenReturn(message);
        when(messageReceiptMapper.insert(any(MessageReceiptEntity.class))).thenReturn(1);
        when(stateMachine.transitStatus("msg-002", MessageStatus.SENDING, MessageStatus.FAILED)).thenReturn(true);

        receiptService.processReceipt(callback);

        verify(stateMachine).transitStatus("msg-002", MessageStatus.SENDING, MessageStatus.FAILED);
    }

    @Test
    void processReceipt_validSignature_read_updatesStatusToSuccess() {
        ReceiptCallback callback = buildCallback("msg-003", "APP_PUSH", "ch-003", "READ");
        String signContent = receiptService.buildSignContent(callback);
        callback.setSignature(HmacSignatureUtil.generateSignature(RECEIPT_SECRET, signContent));

        MessageEntity message = buildMessage("msg-003", MessageStatus.SENDING);
        when(messageMapper.selectByMsgId("msg-003")).thenReturn(message);
        when(messageReceiptMapper.insert(any(MessageReceiptEntity.class))).thenReturn(1);
        when(stateMachine.transitStatus("msg-003", MessageStatus.SENDING, MessageStatus.SUCCESS)).thenReturn(true);

        receiptService.processReceipt(callback);

        verify(stateMachine).transitStatus("msg-003", MessageStatus.SENDING, MessageStatus.SUCCESS);
    }

    @Test
    void processReceipt_validSignature_unknownReceiptStatus_doesNotUpdateMessageStatus() {
        ReceiptCallback callback = buildCallback("msg-004", "SMS", "ch-004", "UNKNOWN");
        String signContent = receiptService.buildSignContent(callback);
        callback.setSignature(HmacSignatureUtil.generateSignature(RECEIPT_SECRET, signContent));

        MessageEntity message = buildMessage("msg-004", MessageStatus.SENDING);
        when(messageMapper.selectByMsgId("msg-004")).thenReturn(message);
        when(messageReceiptMapper.insert(any(MessageReceiptEntity.class))).thenReturn(1);

        receiptService.processReceipt(callback);

        // Receipt inserted but no status transition
        verify(messageReceiptMapper).insert(any(MessageReceiptEntity.class));
        verify(stateMachine, never()).transitStatus(anyString(), any(), any());
    }

    // ========== 终态消息不更新状态 ==========

    @Test
    void processReceipt_terminalStatus_skipsStatusUpdate() {
        ReceiptCallback callback = buildCallback("msg-005", "SMS", "ch-005", "DELIVERED");
        String signContent = receiptService.buildSignContent(callback);
        callback.setSignature(HmacSignatureUtil.generateSignature(RECEIPT_SECRET, signContent));

        MessageEntity message = buildMessage("msg-005", MessageStatus.SUCCESS);
        when(messageMapper.selectByMsgId("msg-005")).thenReturn(message);
        when(messageReceiptMapper.insert(any(MessageReceiptEntity.class))).thenReturn(1);

        receiptService.processReceipt(callback);

        // Receipt inserted but no status transition for terminal state
        verify(messageReceiptMapper).insert(any(MessageReceiptEntity.class));
        verify(stateMachine, never()).transitStatus(anyString(), any(), any());
    }

    // ========== 签名验证失败 → 拒绝请求 ==========

    @Test
    void processReceipt_invalidSignature_throwsException() {
        ReceiptCallback callback = buildCallback("msg-006", "SMS", "ch-006", "DELIVERED");
        callback.setSignature("invalid-signature");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> receiptService.processReceipt(callback));
        assertEquals("回执签名验证失败", ex.getMessage());

        // No DB operations
        verify(messageMapper, never()).selectByMsgId(anyString());
        verify(messageReceiptMapper, never()).insert(any());
    }

    // ========== 消息不存在 → 抛出异常 ==========

    @Test
    void processReceipt_messageNotFound_throwsException() {
        ReceiptCallback callback = buildCallback("msg-007", "SMS", "ch-007", "DELIVERED");
        String signContent = receiptService.buildSignContent(callback);
        callback.setSignature(HmacSignatureUtil.generateSignature(RECEIPT_SECRET, signContent));

        when(messageMapper.selectByMsgId("msg-007")).thenReturn(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> receiptService.processReceipt(callback));
        assertTrue(ex.getMessage().contains("消息不存在"));

        verify(messageReceiptMapper, never()).insert(any());
    }

    // ========== buildSignContent ==========

    @Test
    void buildSignContent_concatenatesFields() {
        ReceiptCallback callback = buildCallback("msg-100", "SMS", "ch-100", "DELIVERED");
        String content = receiptService.buildSignContent(callback);
        assertEquals("msg-100SMSch-100DELIVERED", content);
    }

    // ========== Helper methods ==========

    private ReceiptCallback buildCallback(String msgId, String channel, String channelMsgId, String receiptStatus) {
        return ReceiptCallback.builder()
                .msgId(msgId)
                .channel(channel)
                .channelMsgId(channelMsgId)
                .receiptStatus(receiptStatus)
                .rawData("{\"status\":\"" + receiptStatus + "\"}")
                .build();
    }

    private MessageEntity buildMessage(String msgId, MessageStatus status) {
        return MessageEntity.builder()
                .msgId(msgId)
                .status(status.name())
                .build();
    }
}
