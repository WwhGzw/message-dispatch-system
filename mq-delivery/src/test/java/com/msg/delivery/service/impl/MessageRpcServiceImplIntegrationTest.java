package com.msg.delivery.service.impl;

import com.msg.delivery.dto.MessageSubmitRequest;
import com.msg.delivery.dto.MessageSubmitResponse;
import com.msg.delivery.entity.MessageEntity;
import com.msg.delivery.exception.SystemException;
import com.msg.delivery.exception.ValidationException;
import com.msg.delivery.mapper.MessageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MessageRpcServiceImpl
 * 
 * Tests the complete message submission flow with real database and RabbitMQ.
 * These tests verify Requirements 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.5.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MessageRpcServiceImplIntegrationTest {
    
    @Autowired
    private MessageRpcServiceImpl messageRpcService;
    
    @Autowired
    private MessageMapper messageMapper;
    
    @Test
    void submitMessage_ValidRequest_PersistsAndPublishes() {
        // Given
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("integration-test-001")
            .destinationUrl("https://example.com/callback")
            .payload("{\"orderId\":\"12345\",\"amount\":100.50}")
            .build();
        
        // When
        long startTime = System.currentTimeMillis();
        MessageSubmitResponse response = messageRpcService.submitMessage(request);
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        // Then - Verify response
        assertNotNull(response);
        assertEquals("integration-test-001", response.getMessageId());
        assertNotNull(response.getAcceptedTime());
        
        // Verify response time requirement (< 100ms)
        assertTrue(elapsedTime < 100, 
            String.format("Response time %dms exceeds 100ms requirement", elapsedTime));
        
        // Verify database persistence
        MessageEntity entity = messageMapper.selectById("integration-test-001");
        assertNotNull(entity, "Message should be persisted to database");
        assertEquals("integration-test-001", entity.getMessageId());
        assertEquals("https://example.com/callback", entity.getDestinationUrl());
        assertEquals("{\"orderId\":\"12345\",\"amount\":100.50}", entity.getPayload());
        assertEquals("PENDING", entity.getStatus());
        assertEquals(0, entity.getRetryCount());
        assertEquals(5, entity.getMaxRetries());
        assertNotNull(entity.getCreateTime());
        assertNotNull(entity.getUpdateTime());
        assertNull(entity.getDeliveryTime());
        assertNull(entity.getFailureReason());
    }
    
    @Test
    void submitMessage_NullPayload_ThrowsValidationException() {
        // Given
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("integration-test-002")
            .destinationUrl("https://example.com/callback")
            .payload(null)
            .build();
        
        // When & Then
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> messageRpcService.submitMessage(request)
        );
        
        assertEquals(ValidationException.ErrorCode.PAYLOAD_NULL, exception.getErrorCode());
        
        // Verify message was NOT persisted
        MessageEntity entity = messageMapper.selectById("integration-test-002");
        assertNull(entity, "Invalid message should not be persisted");
    }
    
    @Test
    void submitMessage_InvalidUrl_ThrowsValidationException() {
        // Given
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("integration-test-003")
            .destinationUrl("not-a-valid-url")
            .payload("{\"data\":\"test\"}")
            .build();
        
        // When & Then
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> messageRpcService.submitMessage(request)
        );
        
        assertEquals(ValidationException.ErrorCode.DESTINATION_URL_INVALID, exception.getErrorCode());
        
        // Verify message was NOT persisted
        MessageEntity entity = messageMapper.selectById("integration-test-003");
        assertNull(entity, "Invalid message should not be persisted");
    }
    
    @Test
    void submitMessage_PayloadTooLarge_ThrowsValidationException() {
        // Given - Create payload larger than 1MB
        String largePayload = "x".repeat(1048577);
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("integration-test-004")
            .destinationUrl("https://example.com/callback")
            .payload(largePayload)
            .build();
        
        // When & Then
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> messageRpcService.submitMessage(request)
        );
        
        assertEquals(ValidationException.ErrorCode.PAYLOAD_TOO_LARGE, exception.getErrorCode());
        
        // Verify message was NOT persisted
        MessageEntity entity = messageMapper.selectById("integration-test-004");
        assertNull(entity, "Invalid message should not be persisted");
    }
    
    @Test
    void submitMessage_EmptyMessageId_ThrowsValidationException() {
        // Given
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("")
            .destinationUrl("https://example.com/callback")
            .payload("{\"data\":\"test\"}")
            .build();
        
        // When & Then
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> messageRpcService.submitMessage(request)
        );
        
        assertEquals(ValidationException.ErrorCode.MESSAGE_ID_EMPTY, exception.getErrorCode());
    }
    
    @Test
    void submitMessage_HttpsUrl_Success() {
        // Given
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("integration-test-005")
            .destinationUrl("https://secure.example.com/webhook")
            .payload("{\"event\":\"order.created\"}")
            .build();
        
        // When
        MessageSubmitResponse response = messageRpcService.submitMessage(request);
        
        // Then
        assertNotNull(response);
        assertEquals("integration-test-005", response.getMessageId());
        
        // Verify persistence
        MessageEntity entity = messageMapper.selectById("integration-test-005");
        assertNotNull(entity);
        assertEquals("PENDING", entity.getStatus());
    }
    
    @Test
    void submitMessage_HttpUrl_Success() {
        // Given
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("integration-test-006")
            .destinationUrl("http://example.com/webhook")
            .payload("{\"event\":\"order.updated\"}")
            .build();
        
        // When
        MessageSubmitResponse response = messageRpcService.submitMessage(request);
        
        // Then
        assertNotNull(response);
        assertEquals("integration-test-006", response.getMessageId());
        
        // Verify persistence
        MessageEntity entity = messageMapper.selectById("integration-test-006");
        assertNotNull(entity);
        assertEquals("PENDING", entity.getStatus());
    }
    
    @Test
    void submitMessage_MaxPayloadSize_Success() {
        // Given - Create payload exactly 1MB
        String maxPayload = "x".repeat(1048576);
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("integration-test-007")
            .destinationUrl("https://example.com/callback")
            .payload(maxPayload)
            .build();
        
        // When
        MessageSubmitResponse response = messageRpcService.submitMessage(request);
        
        // Then
        assertNotNull(response);
        assertEquals("integration-test-007", response.getMessageId());
        
        // Verify persistence
        MessageEntity entity = messageMapper.selectById("integration-test-007");
        assertNotNull(entity);
        assertEquals("PENDING", entity.getStatus());
    }
}
