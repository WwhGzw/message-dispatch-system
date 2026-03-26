package com.msg.delivery.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msg.delivery.dto.DeliveryResult;
import com.msg.delivery.dto.DeliveryStatus;
import com.msg.delivery.dto.Message;
import com.msg.delivery.dto.Receipt;
import com.msg.delivery.entity.MessageEntity;
import com.msg.delivery.entity.ReceiptEntity;
import com.msg.delivery.mapper.MessageMapper;
import com.msg.delivery.mapper.ReceiptMapper;
import com.msg.delivery.publisher.RabbitMQPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReceiptProcessor
 * 
 * Tests receipt extraction, processing, and database updates.
 * 
 * @author MQ Delivery System
 */
@ExtendWith(MockitoExtension.class)
class ReceiptProcessorTest {
    
    @Mock
    private ObjectMapper objectMapper;
    
    @Mock
    private RabbitMQPublisher rabbitMQPublisher;
    
    @Mock
    private MessageMapper messageMapper;
    
    @Mock
    private ReceiptMapper receiptMapper;
    
    @InjectMocks
    private ReceiptProcessor receiptProcessor;
    
    private Message testMessage;
    private DeliveryResult testDeliveryResult;
    private MessageEntity testMessageEntity;
    
    @BeforeEach
    void setUp() {
        // Use real ObjectMapper for JSON parsing in tests
        receiptProcessor = new ReceiptProcessor();
        receiptProcessor.objectMapper = new ObjectMapper();
        receiptProcessor.rabbitMQPublisher = rabbitMQPublisher;
        receiptProcessor.messageMapper = messageMapper;
        receiptProcessor.receiptMapper = receiptMapper;
        
        testMessage = Message.builder()
            .messageId("MSG-001")
            .destinationUrl("https://example.com/webhook")
            .payload("{\"data\":\"test\"}")
            .retryCount(0)
            .maxRetries(5)
            .build();
        
        testDeliveryResult = DeliveryResult.builder()
            .messageId("MSG-001")
            .httpStatus(200)
            .status(DeliveryStatus.SUCCESS)
            .latencyMs(150L)
            .build();
        
        testMessageEntity = MessageEntity.builder()
            .messageId("MSG-001")
            .destinationUrl("https://example.com/webhook")
            .payload("{\"data\":\"test\"}")
            .status("PENDING")
            .retryCount(0)
            .maxRetries(5)
            .createTime(LocalDateTime.now())
            .build();
    }
    
    @Test
    void testExtractReceipt_WithValidReceipt() {
        // Given
        String responseBody = "{\"success\":true,\"receipt\":{\"receiptId\":\"R-001\",\"timestamp\":\"2024-01-01T10:00:00\"}}";
        
        // When
        Optional<Receipt> result = receiptProcessor.extractReceipt(responseBody);
        
        // Then
        assertTrue(result.isPresent());
        Receipt receipt = result.get();
        assertNotNull(receipt.getReceiptData());
        assertTrue(receipt.getReceiptData().contains("receiptId"));
        assertTrue(receipt.getReceiptData().contains("R-001"));
        assertNotNull(receipt.getReceiptTime());
    }
    
    @Test
    void testExtractReceipt_WithoutReceiptField() {
        // Given
        String responseBody = "{\"success\":true,\"message\":\"Processed successfully\"}";
        
        // When
        Optional<Receipt> result = receiptProcessor.extractReceipt(responseBody);
        
        // Then
        assertFalse(result.isPresent());
    }
    
    @Test
    void testExtractReceipt_WithNullResponseBody() {
        // When
        Optional<Receipt> result = receiptProcessor.extractReceipt(null);
        
        // Then
        assertFalse(result.isPresent());
    }
    
    @Test
    void testExtractReceipt_WithEmptyResponseBody() {
        // When
        Optional<Receipt> result = receiptProcessor.extractReceipt("");
        
        // Then
        assertFalse(result.isPresent());
    }
    
    @Test
    void testExtractReceipt_WithInvalidJson() {
        // Given
        String responseBody = "invalid json {";
        
        // When
        Optional<Receipt> result = receiptProcessor.extractReceipt(responseBody);
        
        // Then
        assertFalse(result.isPresent());
    }
    
    @Test
    void testExtractReceipt_WithComplexReceiptData() {
        // Given
        String responseBody = "{\"success\":true,\"receipt\":{\"receiptId\":\"R-002\",\"timestamp\":\"2024-01-01T10:00:00\",\"data\":{\"items\":[1,2,3],\"total\":100}}}";
        
        // When
        Optional<Receipt> result = receiptProcessor.extractReceipt(responseBody);
        
        // Then
        assertTrue(result.isPresent());
        Receipt receipt = result.get();
        assertTrue(receipt.getReceiptData().contains("items"));
        assertTrue(receipt.getReceiptData().contains("total"));
    }
    
    @Test
    void testProcessReceipt_WithReceipt() {
        // Given
        String responseBody = "{\"success\":true,\"receipt\":{\"receiptId\":\"R-001\",\"timestamp\":\"2024-01-01T10:00:00\"}}";
        testDeliveryResult.setResponseBody(responseBody);
        
        when(messageMapper.selectById("MSG-001")).thenReturn(testMessageEntity);
        when(messageMapper.updateById(any(MessageEntity.class))).thenReturn(1);
        when(receiptMapper.insert(any(ReceiptEntity.class))).thenReturn(1);
        
        // When
        receiptProcessor.processReceipt(testDeliveryResult, testMessage);
        
        // Then
        // Verify receipt published to queue
        ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
        verify(rabbitMQPublisher).publishToReceiptQueue(receiptCaptor.capture(), eq("MSG-001"));
        Receipt publishedReceipt = receiptCaptor.getValue();
        assertEquals("MSG-001", publishedReceipt.getMessageId());
        assertNotNull(publishedReceipt.getReceiptData());
        
        // Verify receipt persisted to database
        ArgumentCaptor<ReceiptEntity> receiptEntityCaptor = ArgumentCaptor.forClass(ReceiptEntity.class);
        verify(receiptMapper).insert(receiptEntityCaptor.capture());
        ReceiptEntity persistedReceipt = receiptEntityCaptor.getValue();
        assertEquals("MSG-001", persistedReceipt.getMessageId());
        assertFalse(persistedReceipt.getConsumed());
        assertNotNull(persistedReceipt.getCreateTime());
        
        // Verify message status updated to DELIVERED
        ArgumentCaptor<MessageEntity> messageCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageMapper).updateById(messageCaptor.capture());
        MessageEntity updatedMessage = messageCaptor.getValue();
        assertEquals("DELIVERED", updatedMessage.getStatus());
        assertNotNull(updatedMessage.getDeliveryTime());
        assertNotNull(updatedMessage.getUpdateTime());
    }
    
    @Test
    void testProcessReceipt_WithoutReceipt() {
        // Given
        String responseBody = "{\"success\":true,\"message\":\"Processed successfully\"}";
        testDeliveryResult.setResponseBody(responseBody);
        
        when(messageMapper.selectById("MSG-001")).thenReturn(testMessageEntity);
        when(messageMapper.updateById(any(MessageEntity.class))).thenReturn(1);
        
        // When
        receiptProcessor.processReceipt(testDeliveryResult, testMessage);
        
        // Then
        // Verify no receipt published
        verify(rabbitMQPublisher, never()).publishToReceiptQueue(any(), any());
        verify(receiptMapper, never()).insert(any());
        
        // Verify message status still updated to DELIVERED
        ArgumentCaptor<MessageEntity> messageCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageMapper).updateById(messageCaptor.capture());
        MessageEntity updatedMessage = messageCaptor.getValue();
        assertEquals("DELIVERED", updatedMessage.getStatus());
        assertNotNull(updatedMessage.getDeliveryTime());
    }
    
    @Test
    void testProcessReceipt_NullDeliveryResult() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            receiptProcessor.processReceipt(null, testMessage);
        });
    }
    
    @Test
    void testProcessReceipt_NullOriginalMessage() {
        // Given
        testDeliveryResult.setResponseBody("{\"success\":true}");
        
        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            receiptProcessor.processReceipt(testDeliveryResult, null);
        });
    }
    
    @Test
    void testProcessReceipt_MessageNotFoundInDatabase() {
        // Given
        String responseBody = "{\"success\":true,\"receipt\":{\"receiptId\":\"R-001\"}}";
        testDeliveryResult.setResponseBody(responseBody);
        
        when(messageMapper.selectById("MSG-001")).thenReturn(null);
        
        // When
        receiptProcessor.processReceipt(testDeliveryResult, testMessage);
        
        // Then
        // Verify receipt still published
        verify(rabbitMQPublisher).publishToReceiptQueue(any(Receipt.class), eq("MSG-001"));
        verify(receiptMapper).insert(any(ReceiptEntity.class));
        
        // Verify no update attempted (message not found)
        verify(messageMapper, never()).updateById(any(MessageEntity.class));
    }
    
    @Test
    void testProcessReceipt_WithEmptyResponseBody() {
        // Given
        testDeliveryResult.setResponseBody("");
        
        when(messageMapper.selectById("MSG-001")).thenReturn(testMessageEntity);
        when(messageMapper.updateById(any(MessageEntity.class))).thenReturn(1);
        
        // When
        receiptProcessor.processReceipt(testDeliveryResult, testMessage);
        
        // Then
        // Verify no receipt published
        verify(rabbitMQPublisher, never()).publishToReceiptQueue(any(), any());
        
        // Verify message status updated to DELIVERED
        verify(messageMapper).updateById(any(MessageEntity.class));
    }
    
    @Test
    void testProcessReceipt_PublishFailure() {
        // Given
        String responseBody = "{\"success\":true,\"receipt\":{\"receiptId\":\"R-001\"}}";
        testDeliveryResult.setResponseBody(responseBody);
        
        doThrow(new RuntimeException("Queue publish failed"))
            .when(rabbitMQPublisher).publishToReceiptQueue(any(Receipt.class), any(String.class));
        
        // When/Then
        assertThrows(RuntimeException.class, () -> {
            receiptProcessor.processReceipt(testDeliveryResult, testMessage);
        });
    }
    
    @Test
    void testProcessReceipt_DatabaseUpdateFailure() {
        // Given
        String responseBody = "{\"success\":true,\"receipt\":{\"receiptId\":\"R-001\"}}";
        testDeliveryResult.setResponseBody(responseBody);
        
        when(messageMapper.selectById("MSG-001")).thenReturn(testMessageEntity);
        when(messageMapper.updateById(any(MessageEntity.class)))
            .thenThrow(new RuntimeException("Database update failed"));
        when(receiptMapper.insert(any(ReceiptEntity.class))).thenReturn(1);
        
        // When/Then
        assertThrows(RuntimeException.class, () -> {
            receiptProcessor.processReceipt(testDeliveryResult, testMessage);
        });
    }
}
