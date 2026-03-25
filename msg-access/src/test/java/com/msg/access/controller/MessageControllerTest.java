package com.msg.access.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.msg.access.dto.ReceiptCallback;
import com.msg.access.service.MessageService;
import com.msg.common.dto.CancelRequest;
import com.msg.common.dto.CancelResult;
import com.msg.common.dto.DelaySendRequest;
import com.msg.common.dto.MessageStatusVO;
import com.msg.common.dto.SendRequest;
import com.msg.common.dto.SendResult;
import com.msg.common.dto.StatusQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for MessageController
 */
@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private MessageController messageController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(messageController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void sendNow_validRequest_shouldReturn200() throws Exception {
        SendRequest request = SendRequest.builder()
                .bizType("ORDER_NOTIFY")
                .bizId("ORDER_001")
                .channel("SMS")
                .templateCode("order_shipped")
                .receiver("13800001234")
                .variables(Map.of("orderNo", "ORDER_001"))
                .build();

        when(messageService.sendNow(any(SendRequest.class)))
                .thenReturn(SendResult.success("msg_123"));

        mockMvc.perform(post("/msg/send/now")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.msgId").value("msg_123"));

        verify(messageService).sendNow(any(SendRequest.class));
    }

    @Test
    void sendDelay_validRequest_shouldReturn200() throws Exception {
        DelaySendRequest request = DelaySendRequest.builder()
                .bizType("PROMOTION")
                .bizId("PROMO_001")
                .channel("APP_PUSH")
                .templateCode("promotion_push")
                .receiver("device_token_xxx")
                .sendTime(LocalDateTime.now().plusHours(1))
                .build();

        when(messageService.sendDelay(any(DelaySendRequest.class)))
                .thenReturn(SendResult.success("msg_456"));

        mockMvc.perform(post("/msg/send/delay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.msgId").value("msg_456"));

        verify(messageService).sendDelay(any(DelaySendRequest.class));
    }

    @Test
    void queryStatus_withMsgId_shouldReturn200() throws Exception {
        MessageStatusVO statusVO = MessageStatusVO.builder()
                .msgId("msg_123")
                .status("SUCCESS")
                .retryTimes(0)
                .build();

        when(messageService.queryStatus(any(StatusQuery.class)))
                .thenReturn(statusVO);

        mockMvc.perform(get("/msg/status")
                        .param("msgId", "msg_123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.msgId").value("msg_123"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));

        verify(messageService).queryStatus(any(StatusQuery.class));
    }

    @Test
    void cancel_validRequest_shouldReturn200() throws Exception {
        CancelRequest request = CancelRequest.builder()
                .msgId("msg_123")
                .reason("用户取消订单")
                .build();

        when(messageService.cancel(any(CancelRequest.class)))
                .thenReturn(CancelResult.success());

        mockMvc.perform(post("/msg/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.success").value(true));

        verify(messageService).cancel(any(CancelRequest.class));
    }

    @Test
    void handleReceipt_validCallback_shouldReturn200() throws Exception {
        ReceiptCallback callback = ReceiptCallback.builder()
                .msgId("msg_123")
                .channel("SMS")
                .channelMsgId("ch_msg_001")
                .receiptStatus("DELIVERED")
                .signature("abc123")
                .rawData("{\"status\":\"delivered\"}")
                .build();

        doNothing().when(messageService).handleReceipt(any(ReceiptCallback.class));

        mockMvc.perform(post("/msg/callback/receipt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(messageService).handleReceipt(any(ReceiptCallback.class));
    }

    @Test
    void sendNow_delegatesToService() throws Exception {
        when(messageService.sendNow(any(SendRequest.class)))
                .thenReturn(SendResult.fail("service error"));

        SendRequest request = SendRequest.builder()
                .bizType("TEST")
                .bizId("BIZ_001")
                .channel("EMAIL")
                .templateCode("test_tpl")
                .receiver("test@example.com")
                .build();

        mockMvc.perform(post("/msg/send/now")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(false))
                .andExpect(jsonPath("$.data.message").value("service error"));
    }
}
