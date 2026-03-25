package com.msg.access.service.impl;

import com.msg.access.dto.ReceiptCallback;
import com.msg.center.service.MessageCenterService;
import com.msg.center.service.ReceiptService;
import com.msg.common.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MessageServiceImpl 单元测试
 * 验证接入层到核心层的委托桥接正确性
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {

    @Mock
    private MessageCenterService messageCenterService;

    @Mock
    private ReceiptService receiptService;

    private MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageServiceImpl(messageCenterService, receiptService);
    }

    @Test
    void sendNow_delegatesToMessageCenterService() {
        SendRequest request = SendRequest.builder()
                .bizType("ORDER").bizId("ORD_001").channel("SMS")
                .templateCode("tpl").receiver("13800001234")
                .variables(Map.of("k", "v")).build();
        SendResult expected = SendResult.success("msg-123");
        when(messageCenterService.processSendNow(request)).thenReturn(expected);

        SendResult result = messageService.sendNow(request);

        assertSame(expected, result);
        verify(messageCenterService).processSendNow(request);
    }

    @Test
    void sendDelay_delegatesToMessageCenterService() {
        DelaySendRequest request = DelaySendRequest.builder()
                .bizType("PROMO").bizId("P_001").channel("EMAIL")
                .templateCode("tpl").receiver("a@b.com")
                .sendTime(LocalDateTime.now().plusHours(1)).build();
        SendResult expected = SendResult.success("msg-456");
        when(messageCenterService.processSendDelay(request)).thenReturn(expected);

        SendResult result = messageService.sendDelay(request);

        assertSame(expected, result);
        verify(messageCenterService).processSendDelay(request);
    }

    @Test
    void queryStatus_delegatesToMessageCenterService() {
        StatusQuery query = StatusQuery.builder().msgId("msg-789").build();
        MessageStatusVO expected = MessageStatusVO.builder()
                .msgId("msg-789").status("SUCCESS").retryTimes(0).build();
        when(messageCenterService.queryStatus(query)).thenReturn(expected);

        MessageStatusVO result = messageService.queryStatus(query);

        assertSame(expected, result);
        verify(messageCenterService).queryStatus(query);
    }

    @Test
    void cancel_delegatesToMessageCenterService() {
        CancelRequest request = CancelRequest.builder().msgId("msg-100").build();
        CancelResult expected = CancelResult.success();
        when(messageCenterService.cancelMessage(request)).thenReturn(expected);

        CancelResult result = messageService.cancel(request);

        assertSame(expected, result);
        verify(messageCenterService).cancelMessage(request);
    }

    @Test
    void handleReceipt_delegatesToReceiptService() {
        ReceiptCallback callback = ReceiptCallback.builder()
                .msgId("msg-200").channel("SMS")
                .channelMsgId("ch-001").receiptStatus("DELIVERED")
                .signature("sig").rawData("{}").build();

        messageService.handleReceipt(callback);

        verify(receiptService).processReceipt(callback);
    }
}
