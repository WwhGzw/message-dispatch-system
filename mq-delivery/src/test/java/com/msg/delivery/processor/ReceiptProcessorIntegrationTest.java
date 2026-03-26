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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Integration tests for ReceiptProcessor
 * 
 * Tests the complete receipt processing flow with Spring context,
 * verifying integration with RabbitMQ publisher and database mappers.
 * 
 * @author MQ Delivery System
 */
@SpringBootTest
@ActiveProfiles("test")
class ReceiptProcessorIntegrationTest {
    
    @Autowired
    private ReceiptProcessor receiptProcessor;
    
    @MockBean
    private RabbitMQPublisher rabbitMQPublisher;
    
    @MockBean
    private MessageMapper messageMapper;
    
    @MockBean
    private ReceiptMapper receiptMapper;
    
    private Message testMessage;
    private MessageEntity testMessageEntity;
    
    @BeforeEach
    void setUp() {
        testMessage = Message.builder()
            .messageId("MSG-INT-001")
            .destinationUrl("https://example.com/webhook")
            .payload("{\"data\":\"integration test\"}")
            .retryCount(0)
            .maxRetries(5)
            .build();
        
        testMessageEntity = MessageEntity.builder()
            .messageId("MSG-INT-001")
            .destinationUrl("https://example.com/webhook")
            .payload("{\"data\":\"integration test\"}")
            .status("PENDING")
            .retryCount(0)
            .maxRetries(5)
            .createTime(LocalDateTime.now())
            .build();
    }
    
    @Test
    void testCompleteReceiptProcessingFlow() {
        // Given: A successful delivery with receipt
        String responseBody = "{\"success\":true,\"receipt\":{\"receiptId\":\"R-INT-001\",\"timestamp\":\"2024-01-01T10:00:00\",\"data\":{\"status\":\"processed\"}}}";
        
        DeliveryResult deliveryResult = DeliveryResult.builder()
            .messageId("MSG-INT-001")
            .httpStatus(200)
            .responseBody(responseBody)
            .status(DeliveryStatus.SUCCESS)
            .latencyMs(150L)
            .build();
        
        when(messageMapper.selectById("MSG-INT-001")).thenReturn(testMessageEntity);
        when(messageMapper.updateById(any(MessageEntity.class))).thenReturn(1);
        when(receiptMapper.insert(any(ReceiptEntity.class))).thenReturn(1);
        
        // When: Processing the receipt
        receiptProcessor.processReceipt(deliveryResult, testMessage);
        
        // Then: Verify complete flow
        // 1. Receipt published to queue
        verify(rabbitMQPublisher, times(1))
            .publishToReceiptQueue(any(Receipt.class), eq("MSG-INT-001"));
        
        // 2. Receipt persisted to database
        verify(receiptMapper, times(1)).insert(any(ReceiptEntity.class));
        
        // 3. Message status updated
        verify(messageMapper, times(1)).selectById("MSG-INT-001");
        verify(messageMapper, times(1)).updateById(any(MessageEntity.class));
    }
    
    @Test
    void testReceiptProcessingWithoutReceipt() {
        // Given: A successful delivery without receipt
        String responseBody = "{\"success\":true,\"message\":\"Processed successfully\"}";
        
        DeliveryResult deliveryResult = DeliveryResult.builder()
            .messageId("MSG-INT-002")
            .httpStatus(200)
            .responseBody(responseBody)
            .status(DeliveryStatus.SUCCESS)
            .latencyMs(100L)
            .build();
        
        testMessage.setMessageId("MSG-INT-002");
        testMessageEntity.setMessageId("MSG-INT-002");
        
        when(messageMapper.selectById("MSG-INT-002")).thenReturn(testMessageEntity);
        when(messageMapper.updateById(any(MessageEntity.class))).thenReturn(1);
        
        // When: Processing the receipt
        receiptProcessor.processReceipt(deliveryResult, testMessage);
        
        // Then: Verify flow without receipt
        // 1. No receipt published
        verify(rabbitMQPublisher, never()).publishToReceiptQueue(any(), any());
        verify(receiptMapper, never()).insert(any());
        
        // 2. Message status still updated
        verify(messageMapper, times(1)).updateById(any(MessageEntity.class));
    }
    
    @Test
    void testReceiptExtractionWithComplexJson() {
        // Given: Complex JSON response with nested receipt data
        String responseBody = "{\"success\":true,\"receipt\":{\"receiptId\":\"R-COMPLEX-001\",\"timestamp\":\"2024-01-01T10:00:00\",\"metadata\":{\"items\":[{\"id\":1,\"name\":\"item1\"},{\"id\":2,\"name\":\"item2\"}],\"total\":100,\"currency\":\"USD\"}}}";
        
        // When: Extracting receipt
        Optional<Receipt> result = receiptProcessor.extractReceipt(responseBody);
        
        // Then: Receipt should be extracted with all nested data
        assertTrue(result.isPresent());
        Receipt receipt = result.get();
        assertNotNull(receipt.getReceiptData());
        assertTrue(receipt.getReceiptData().contains("receiptId"));
        assertTrue(receipt.getReceiptData().contains("metadata"));
        assertTrue(receipt.getReceiptData().contains("items"));
    }
    
    @Test
    void testReceiptProcessingIdempotency() {
        // Given: Same delivery result processed multiple times
        String responseBody = "{\"success\":true,\"receipt\":{\"receiptId\":\"R-IDEM-001\"}}";
        
        DeliveryResult deliveryResult = DeliveryResult.builder()
            .messageId("MSG-IDEM-001")
            .httpStatus(200)
            .responseBody(responseBody)
            .status(DeliveryStatus.SUCCESS)
            .build();
        
        testMessage.setMessageId("MSG-IDEM-001");
        testMessageEntity.setMessageId("MSG-IDEM-001");
        
        when(messageMapper.selectById("MSG-IDEM-001")).thenReturn(testMessageEntity);
        when(messageMapper.updateById(any(MessageEntity.class))).thenReturn(1);
        when(receiptMapper.insert(any(ReceiptEntity.class))).thenReturn(1);
        
        // When: Processing the same receipt twice
        receiptProcessor.processReceipt(deliveryResult, testMessage);
        receiptProcessor.processReceipt(deliveryResult, testMessage);
        
        // Then: Both attempts should succeed (idempotent)
        verify(rabbitMQPublisher, times(2))
            .publishToReceiptQueue(any(Receipt.class), eq("MSG-IDEM-001"));
        verify(receiptMapper, times(2)).insert(any(ReceiptEntity.class));
        verify(messageMapper, times(2)).updateById(any(MessageEntity.class));
    }
    
    @Test
    void testReceiptProcessingWithVariousHttpStatuses() {
        // Test with different 2xx status codes
        int[] successStatuses = {200, 201, 202, 204, 299};
        
        for (int status : successStatuses) {
            String messageId = "MSG-STATUS-" + status;
            String responseBody = "{\"success\":true,\"receipt\":{\"receiptId\":\"R-" + status + "\"}}";
            
            DeliveryResult deliveryResult = DeliveryResult.builder()
                .messageId(messageId)
                .httpStatus(status)
                .responseBody(responseBody)
                .status(DeliveryStatus.SUCCESS)
                .build();
            
            Message message = Message.builder()
                .messageId(messageId)
                .destinationUrl("https://example.com/webhook")
                .payload("{\"data\":\"test\"}")
                .build();
            
            MessageEntity messageEntity = MessageEntity.builder()
                .messageId(messageId)
                .status("PENDING")
                .build();
            
            when(messageMapper.selectById(messageId)).thenReturn(messageEntity);
            when(messageMapper.updateById(any(MessageEntity.class))).thenReturn(1);
            when(receiptMapper.insert(any(ReceiptEntity.class))).thenReturn(1);
            
            // When: Processing receipt
            receiptProcessor.processReceipt(deliveryResult, message);
            
            // Then: Should process successfully for all 2xx statuses
            verify(rabbitMQPublisher, times(1))
                .publishToReceiptQueue(any(Receipt.class), eq(messageId));
        }
    }
}
