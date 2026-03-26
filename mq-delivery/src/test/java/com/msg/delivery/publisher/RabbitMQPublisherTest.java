package com.msg.delivery.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msg.delivery.dto.Message;
import com.msg.delivery.dto.Receipt;
import com.msg.delivery.dto.RetryAttempt;
import com.msg.delivery.exception.QueuePublishException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RabbitMQPublisher
 * 
 * Tests message publishing to main queue, receipt queue, and dead letter queue
 * with proper error handling and metadata inclusion.
 */
@ExtendWith(MockitoExtension.class)
class RabbitMQPublisherTest {
    
    @Mock
    private RabbitTemplate rabbitTemplate;
    
    @Mock
    private ObjectMapper objectMapper;
    
    @InjectMocks
    private RabbitMQPublisher publisher;
    
    private Message testMessage;
    private Receipt testReceipt;
    private List<RetryAttempt> testRetryHistory;
    
    @BeforeEach
    void setUp() {
        testMessage = Message.builder()
            .messageId("test-msg-001")
            .destinationUrl("https://example.com/webhook")
            .payload("{\"data\":\"test\"}")
            .retryCount(0)
            .maxRetries(5)
            .build();
        
        testReceipt = Receipt.builder()
            .messageId("test-msg-001")
            .receiptData("{\"receiptId\":\"receipt-001\"}")
            .receiptTime(LocalDateTime.now())
            .build();
        
        testRetryHistory = new ArrayList<>();
        testRetryHistory.add(RetryAttempt.builder()
            .attemptNumber(1)
            .httpStatus(500)
            .deliveryResult("HTTP_ERROR")
            .errorMessage("Internal Server Error")
            .attemptTime(LocalDateTime.now())
            .latencyMs(1000L)
            .build());
    }
    
    @Test
    void testPublishToMainQueue_Success() {
        // When
        publisher.publishToMainQueue(testMessage);
        
        // Then
        verify(rabbitTemplate, times(1)).convertAndSend(
            eq("mq.delivery.exchange.main"),
            eq("mq.delivery.routing.main"),
            eq(testMessage),
            any(MessagePostProcessor.class)
        );
    }
    
    @Test
    void testPublishToMainQueue_NullMessage_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            publisher.publishToMainQueue(null);
        });
        
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }
    
    @Test
    void testPublishToMainQueue_RabbitTemplateThrowsException_ThrowsQueuePublishException() {
        // Given
        doThrow(new RuntimeException("Connection failed"))
            .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
        
        // When & Then
        QueuePublishException exception = assertThrows(QueuePublishException.class, () -> {
            publisher.publishToMainQueue(testMessage);
        });
        
        assertTrue(exception.getMessage().contains("test-msg-001"));
        assertNotNull(exception.getCause());
    }
    
    @Test
    void testPublishToReceiptQueue_Success() {
        // When
        publisher.publishToReceiptQueue(testReceipt, "test-msg-001");
        
        // Then
        verify(rabbitTemplate, times(1)).convertAndSend(
            eq("mq.delivery.exchange.receipt"),
            eq("mq.delivery.routing.receipt"),
            eq(testReceipt),
            any(MessagePostProcessor.class)
        );
    }
    
    @Test
    void testPublishToReceiptQueue_NullReceipt_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            publisher.publishToReceiptQueue(null, "test-msg-001");
        });
        
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }
    
    @Test
    void testPublishToReceiptQueue_NullMessageId_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            publisher.publishToReceiptQueue(testReceipt, null);
        });
        
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }
    
    @Test
    void testPublishToReceiptQueue_EmptyMessageId_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            publisher.publishToReceiptQueue(testReceipt, "");
        });
        
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }
    
    @Test
    void testPublishToReceiptQueue_RabbitTemplateThrowsException_ThrowsQueuePublishException() {
        // Given
        doThrow(new RuntimeException("Connection failed"))
            .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
        
        // When & Then
        QueuePublishException exception = assertThrows(QueuePublishException.class, () -> {
            publisher.publishToReceiptQueue(testReceipt, "test-msg-001");
        });
        
        assertTrue(exception.getMessage().contains("test-msg-001"));
        assertNotNull(exception.getCause());
    }
    
    @Test
    void testPublishToDeadLetterQueue_Success() throws Exception {
        // Given
        String retryHistoryJson = "[{\"attemptNumber\":1}]";
        when(objectMapper.writeValueAsString(testRetryHistory)).thenReturn(retryHistoryJson);
        
        // When
        publisher.publishToDeadLetterQueue(testMessage, "Max retries exceeded", testRetryHistory);
        
        // Then
        verify(rabbitTemplate, times(1)).convertAndSend(
            eq("mq.delivery.exchange.dlq"),
            eq("mq.delivery.routing.dlq"),
            eq(testMessage),
            any(MessagePostProcessor.class)
        );
        verify(objectMapper, times(1)).writeValueAsString(testRetryHistory);
    }
    
    @Test
    void testPublishToDeadLetterQueue_NullMessage_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            publisher.publishToDeadLetterQueue(null, "Failure reason", testRetryHistory);
        });
        
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }
    
    @Test
    void testPublishToDeadLetterQueue_NullFailureReason_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            publisher.publishToDeadLetterQueue(testMessage, null, testRetryHistory);
        });
        
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }
    
    @Test
    void testPublishToDeadLetterQueue_EmptyFailureReason_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            publisher.publishToDeadLetterQueue(testMessage, "", testRetryHistory);
        });
        
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }
    
    @Test
    void testPublishToDeadLetterQueue_NullRetryHistory_ThrowsException() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            publisher.publishToDeadLetterQueue(testMessage, "Failure reason", null);
        });
        
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
    }
    
    @Test
    void testPublishToDeadLetterQueue_SerializationFails_StillPublishes() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(testRetryHistory))
            .thenThrow(new RuntimeException("Serialization failed"));
        
        // When
        publisher.publishToDeadLetterQueue(testMessage, "Max retries exceeded", testRetryHistory);
        
        // Then - should still publish with empty retry history
        verify(rabbitTemplate, times(1)).convertAndSend(
            eq("mq.delivery.exchange.dlq"),
            eq("mq.delivery.routing.dlq"),
            eq(testMessage),
            any(MessagePostProcessor.class)
        );
    }
    
    @Test
    void testPublishToDeadLetterQueue_RabbitTemplateThrowsException_ThrowsQueuePublishException() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(testRetryHistory)).thenReturn("[]");
        doThrow(new RuntimeException("Connection failed"))
            .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(), any(MessagePostProcessor.class));
        
        // When & Then
        QueuePublishException exception = assertThrows(QueuePublishException.class, () -> {
            publisher.publishToDeadLetterQueue(testMessage, "Max retries exceeded", testRetryHistory);
        });
        
        assertTrue(exception.getMessage().contains("test-msg-001"));
        assertNotNull(exception.getCause());
    }
    
    @Test
    void testPublishToDeadLetterQueue_EmptyRetryHistory_Success() throws Exception {
        // Given
        List<RetryAttempt> emptyHistory = new ArrayList<>();
        when(objectMapper.writeValueAsString(emptyHistory)).thenReturn("[]");
        
        // When
        publisher.publishToDeadLetterQueue(testMessage, "Immediate failure", emptyHistory);
        
        // Then
        verify(rabbitTemplate, times(1)).convertAndSend(
            eq("mq.delivery.exchange.dlq"),
            eq("mq.delivery.routing.dlq"),
            eq(testMessage),
            any(MessagePostProcessor.class)
        );
    }
}
