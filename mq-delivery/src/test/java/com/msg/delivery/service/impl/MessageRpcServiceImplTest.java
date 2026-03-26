package com.msg.delivery.service.impl;

import com.msg.delivery.dto.Message;
import com.msg.delivery.dto.MessageSubmitRequest;
import com.msg.delivery.dto.MessageSubmitResponse;
import com.msg.delivery.entity.MessageEntity;
import com.msg.delivery.exception.QueuePublishException;
import com.msg.delivery.exception.SystemException;
import com.msg.delivery.exception.ValidationException;
import com.msg.delivery.mapper.MessageMapper;
import com.msg.delivery.publisher.RabbitMQPublisher;
import com.msg.delivery.validator.MessageValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MessageRpcServiceImpl
 * 
 * Tests message submission flow including validation, persistence,
 * queue publishing, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class MessageRpcServiceImplTest {
    
    @Mock
    private MessageValidator messageValidator;
    
    @Mock
    private MessageMapper messageMapper;
    
    @Mock
    private RabbitMQPublisher rabbitMQPublisher;
    
    @InjectMocks
    private MessageRpcServiceImpl messageRpcService;
    
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(messageRpcService, "maxRetries", 5);
    }
    
    @Test
    void submitMessage_ValidRequest_Success() {
        // Given
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("msg-001")
            .destinationUrl("https://example.com/callback")
            .payload("{\"data\":\"test\"}")
            .build();
        
        when(messageMapper.insert(any(MessageEntity.class))).thenReturn(1);
        doNothing().when(messageValidator).validate(any(Message.class));
        doNothing().when(rabbitMQPublisher).publishToMainQueue(any(Message.class));
        
        // When
        long startTime = System.currentTimeMillis();
        MessageSubmitResponse response = messageRpcService.submitMessage(request);
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        // Then
        assertNotNull(response);
        assertEquals("msg-001", response.getMessageId());
        assertNotNull(response.getAcceptedTime());
        assertTrue(elapsedTime < 100, "Response time should be less than 100ms");
        
        // Verify interactions
        verify(messageValidator, times(1)).validate(any(Message.class));
        verify(messageMapper, times(1)).insert(any(MessageEntity.class));
        verify(rabbitMQPublisher, times(1)).publishToMainQueue(any(Message.class));
        
        // Verify entity fields
        ArgumentCaptor<MessageEntity> entityCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageMapper).insert(entityCaptor.capture());
        MessageEntity capturedEntity = entityCaptor.getValue();
        
        assertEquals("msg-001", capturedEntity.getMessageId());
        assertEquals("https://example.com/callback", capturedEntity.getDestinationUrl());
        assertEquals("{\"data\":\"test\"}", capturedEntity.getPayload());
        assertEquals("PENDING", capturedEntity.getStatus());
        assertEquals(0, capturedEntity.getRetryCount());
        assertEquals(5, capturedEntity.getMaxRetries());
        assertNotNull(capturedEntity.getCreateTime());
        assertNotNull(capturedEntity.getUpdateTime());
    }
    
    @Test
    void submitMessage_ValidationFails_ThrowsValidationException() {
        // Given
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("msg-002")
            .destinationUrl("invalid-url")
            .payload("{\"data\":\"test\"}")
            .build();
        
        doThrow(new ValidationException(
            ValidationException.ErrorCode.DESTINATION_URL_INVALID,
            "Destination URL must be a valid HTTP or HTTPS URL"
        )).when(messageValidator).validate(any(Message.class));
        
        // When & Then
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> messageRpcService.submitMessage(request)
        );
        
        assertEquals(ValidationException.ErrorCode.DESTINATION_URL_INVALID, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("valid HTTP or HTTPS URL"));
        
        // Verify no persistence or publishing occurred
        verify(messageValidator, times(1)).validate(any(Message.class));
        verify(messageMapper, never()).insert(any(MessageEntity.class));
        verify(rabbitMQPublisher, never()).publishToMainQueue(any(Message.class));
    }
    
    @Test
    void submitMessage_DatabaseUnavailable_ThrowsSystemException() {
        // Given
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("msg-003")
            .destinationUrl("https://example.com/callback")
            .payload("{\"data\":\"test\"}")
            .build();
        
        doNothing().when(messageValidator).validate(any(Message.class));
        when(messageMapper.insert(any(MessageEntity.class)))
            .thenThrow(new DataAccessException("Database connection failed") {});
        
        // When & Then
        SystemException exception = assertThrows(
            SystemException.class,
            () -> messageRpcService.submitMessage(request)
        );
        
        assertEquals(SystemException.ErrorCode.DB_UNAVAILABLE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Failed to persist message to database"));
        
        // Verify validation occurred but no publishing
        verify(messageValidator, times(1)).validate(any(Message.class));
        verify(messageMapper, times(1)).insert(any(MessageEntity.class));
        verify(rabbitMQPublisher, never()).publishToMainQueue(any(Message.class));
    }
    
    @Test
    void submitMessage_RabbitMQUnavailable_ThrowsSystemException() {
        // Given
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("msg-004")
            .destinationUrl("https://example.com/callback")
            .payload("{\"data\":\"test\"}")
            .build();
        
        doNothing().when(messageValidator).validate(any(Message.class));
        when(messageMapper.insert(any(MessageEntity.class))).thenReturn(1);
        doThrow(new QueuePublishException("RabbitMQ connection failed", new RuntimeException()))
            .when(rabbitMQPublisher).publishToMainQueue(any(Message.class));
        
        // When & Then
        SystemException exception = assertThrows(
            SystemException.class,
            () -> messageRpcService.submitMessage(request)
        );
        
        assertEquals(SystemException.ErrorCode.MQ_UNAVAILABLE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Failed to publish message to main queue"));
        
        // Verify all steps up to publishing occurred
        verify(messageValidator, times(1)).validate(any(Message.class));
        verify(messageMapper, times(1)).insert(any(MessageEntity.class));
        verify(rabbitMQPublisher, times(1)).publishToMainQueue(any(Message.class));
    }
    
    @Test
    void submitMessage_NullPayload_ThrowsValidationException() {
        // Given
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("msg-005")
            .destinationUrl("https://example.com/callback")
            .payload(null)
            .build();
        
        doThrow(new ValidationException(
            ValidationException.ErrorCode.PAYLOAD_NULL,
            "Payload cannot be null"
        )).when(messageValidator).validate(any(Message.class));
        
        // When & Then
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> messageRpcService.submitMessage(request)
        );
        
        assertEquals(ValidationException.ErrorCode.PAYLOAD_NULL, exception.getErrorCode());
        verify(messageMapper, never()).insert(any(MessageEntity.class));
    }
    
    @Test
    void submitMessage_PayloadTooLarge_ThrowsValidationException() {
        // Given
        String largePayload = "x".repeat(1048577); // 1MB + 1 byte
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("msg-006")
            .destinationUrl("https://example.com/callback")
            .payload(largePayload)
            .build();
        
        doThrow(new ValidationException(
            ValidationException.ErrorCode.PAYLOAD_TOO_LARGE,
            "Payload size exceeds maximum allowed size"
        )).when(messageValidator).validate(any(Message.class));
        
        // When & Then
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> messageRpcService.submitMessage(request)
        );
        
        assertEquals(ValidationException.ErrorCode.PAYLOAD_TOO_LARGE, exception.getErrorCode());
        verify(messageMapper, never()).insert(any(MessageEntity.class));
    }
    
    @Test
    void submitMessage_MessageIdNull_ThrowsValidationException() {
        // Given
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId(null)
            .destinationUrl("https://example.com/callback")
            .payload("{\"data\":\"test\"}")
            .build();
        
        doThrow(new ValidationException(
            ValidationException.ErrorCode.MESSAGE_ID_NULL,
            "Message ID cannot be null"
        )).when(messageValidator).validate(any(Message.class));
        
        // When & Then
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> messageRpcService.submitMessage(request)
        );
        
        assertEquals(ValidationException.ErrorCode.MESSAGE_ID_NULL, exception.getErrorCode());
        verify(messageMapper, never()).insert(any(MessageEntity.class));
    }
    
    @Test
    void submitMessage_VerifyMessageConversion() {
        // Given
        MessageSubmitRequest request = MessageSubmitRequest.builder()
            .messageId("msg-007")
            .destinationUrl("https://example.com/callback")
            .payload("{\"data\":\"test\"}")
            .build();
        
        when(messageMapper.insert(any(MessageEntity.class))).thenReturn(1);
        doNothing().when(messageValidator).validate(any(Message.class));
        doNothing().when(rabbitMQPublisher).publishToMainQueue(any(Message.class));
        
        // When
        messageRpcService.submitMessage(request);
        
        // Then - Verify Message DTO passed to publisher
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitMQPublisher).publishToMainQueue(messageCaptor.capture());
        Message capturedMessage = messageCaptor.getValue();
        
        assertEquals("msg-007", capturedMessage.getMessageId());
        assertEquals("https://example.com/callback", capturedMessage.getDestinationUrl());
        assertEquals("{\"data\":\"test\"}", capturedMessage.getPayload());
        assertEquals(0, capturedMessage.getRetryCount());
        assertEquals(5, capturedMessage.getMaxRetries());
    }
}
