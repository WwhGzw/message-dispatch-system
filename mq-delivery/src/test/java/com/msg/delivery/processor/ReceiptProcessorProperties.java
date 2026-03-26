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
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-Based Tests for ReceiptProcessor
 * 
 * Tests universal properties that should hold across all valid inputs
 * for receipt extraction and processing.
 * 
 * **Validates: Requirements 4.1, 4.2, 4.3, 3.4**
 * 
 * @author MQ Delivery System
 */
class ReceiptProcessorProperties {
    
    private ReceiptProcessor receiptProcessor;
    private RabbitMQPublisher rabbitMQPublisher;
    private MessageMapper messageMapper;
    private ReceiptMapper receiptMapper;
    
    @BeforeEach
    void setUp() {
        rabbitMQPublisher = mock(RabbitMQPublisher.class);
        messageMapper = mock(MessageMapper.class);
        receiptMapper = mock(ReceiptMapper.class);
        
        receiptProcessor = new ReceiptProcessor();
        receiptProcessor.objectMapper = new ObjectMapper();
        receiptProcessor.rabbitMQPublisher = rabbitMQPublisher;
        receiptProcessor.messageMapper = messageMapper;
        receiptProcessor.receiptMapper = receiptMapper;
    }
    
    /**
     * Property 6: Receipt Processing Round Trip
     * 
     * For any HTTP response with status 200-299 containing a receipt in the response body,
     * the system should extract the receipt, publish it to the receipt queue with the
     * original message identifier, and the receipt should be consumable by upstream systems.
     * 
     * **Validates: Requirements 4.1, 4.2, 4.3**
     */
    @Property(tries = 100)
    @Label("Property 6: Receipt Processing Round Trip - Receipt extraction and publishing")
    void receiptProcessingRoundTripProperty(
        @ForAll("validMessageIds") String messageId,
        @ForAll("validReceiptIds") String receiptId,
        @ForAll("successHttpStatus") int httpStatus
    ) {
        // Given: A successful delivery result with a receipt in response body
        String responseBody = String.format(
            "{\"success\":true,\"receipt\":{\"receiptId\":\"%s\",\"timestamp\":\"2024-01-01T10:00:00\"}}",
            receiptId
        );
        
        Message message = Message.builder()
            .messageId(messageId)
            .destinationUrl("https://example.com/webhook")
            .payload("{\"data\":\"test\"}")
            .retryCount(0)
            .maxRetries(5)
            .build();
        
        DeliveryResult deliveryResult = DeliveryResult.builder()
            .messageId(messageId)
            .httpStatus(httpStatus)
            .responseBody(responseBody)
            .status(DeliveryStatus.SUCCESS)
            .latencyMs(100L)
            .build();
        
        MessageEntity messageEntity = MessageEntity.builder()
            .messageId(messageId)
            .status("PENDING")
            .build();
        
        when(messageMapper.selectById(messageId)).thenReturn(messageEntity);
        when(messageMapper.updateById(any(MessageEntity.class))).thenReturn(1);
        when(receiptMapper.insert(any(ReceiptEntity.class))).thenReturn(1);
        
        // When: Processing the receipt
        receiptProcessor.processReceipt(deliveryResult, message);
        
        // Then: Receipt should be published with original message ID
        ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
        verify(rabbitMQPublisher).publishToReceiptQueue(receiptCaptor.capture(), eq(messageId));
        
        Receipt publishedReceipt = receiptCaptor.getValue();
        assertEquals(messageId, publishedReceipt.getMessageId(), 
            "Receipt should be associated with original message ID");
        assertNotNull(publishedReceipt.getReceiptData(), 
            "Receipt data should not be null");
        assertTrue(publishedReceipt.getReceiptData().contains(receiptId), 
            "Receipt data should contain the receipt ID");
        
        // And: Receipt should be persisted to database
        ArgumentCaptor<ReceiptEntity> receiptEntityCaptor = ArgumentCaptor.forClass(ReceiptEntity.class);
        verify(receiptMapper).insert(receiptEntityCaptor.capture());
        
        ReceiptEntity persistedReceipt = receiptEntityCaptor.getValue();
        assertEquals(messageId, persistedReceipt.getMessageId(), 
            "Persisted receipt should have correct message ID");
        assertFalse(persistedReceipt.getConsumed(), 
            "Receipt should initially be unconsumed");
        
        // And: Message status should be updated to DELIVERED
        ArgumentCaptor<MessageEntity> messageCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageMapper).updateById(messageCaptor.capture());
        
        MessageEntity updatedMessage = messageCaptor.getValue();
        assertEquals("DELIVERED", updatedMessage.getStatus(), 
            "Message status should be DELIVERED");
        assertNotNull(updatedMessage.getDeliveryTime(), 
            "Delivery timestamp should be recorded");
    }
    
    /**
     * Property: Receipt Extraction Consistency
     * 
     * For any valid JSON response containing a receipt field, extractReceipt should
     * always return a non-empty Optional with receipt data.
     * 
     * **Validates: Requirements 4.1**
     */
    @Property(tries = 100)
    @Label("Receipt extraction should be consistent for valid JSON with receipt field")
    void receiptExtractionConsistencyProperty(
        @ForAll("validReceiptIds") String receiptId,
        @ForAll("validTimestamps") String timestamp
    ) {
        // Given: A valid JSON response with receipt field
        String responseBody = String.format(
            "{\"success\":true,\"receipt\":{\"receiptId\":\"%s\",\"timestamp\":\"%s\"}}",
            receiptId, timestamp
        );
        
        // When: Extracting receipt
        Optional<Receipt> result = receiptProcessor.extractReceipt(responseBody);
        
        // Then: Receipt should be present
        assertTrue(result.isPresent(), 
            "Receipt should be extracted from valid JSON with receipt field");
        
        Receipt receipt = result.get();
        assertNotNull(receipt.getReceiptData(), 
            "Receipt data should not be null");
        assertNotNull(receipt.getReceiptTime(), 
            "Receipt time should be set");
        assertTrue(receipt.getReceiptData().contains(receiptId), 
            "Receipt data should contain the receipt ID");
    }
    
    /**
     * Property: Receipt Extraction Robustness
     * 
     * For any JSON response without a receipt field, extractReceipt should
     * always return an empty Optional without throwing exceptions.
     * 
     * **Validates: Requirements 4.1**
     */
    @Property(tries = 100)
    @Label("Receipt extraction should handle missing receipt field gracefully")
    void receiptExtractionRobustnessProperty(
        @ForAll("jsonWithoutReceipt") String responseBody
    ) {
        // When: Extracting receipt from response without receipt field
        Optional<Receipt> result = receiptProcessor.extractReceipt(responseBody);
        
        // Then: Should return empty Optional without exception
        assertFalse(result.isPresent(), 
            "Receipt should not be present when receipt field is missing");
    }
    
    /**
     * Property: Message Status Update Consistency
     * 
     * For any successful delivery (with or without receipt), the message status
     * should always be updated to DELIVERED with a delivery timestamp.
     * 
     * **Validates: Requirements 3.4, 4.3**
     */
    @Property(tries = 100)
    @Label("Message status should always be updated to DELIVERED after successful delivery")
    void messageStatusUpdateConsistencyProperty(
        @ForAll("validMessageIds") String messageId,
        @ForAll("successHttpStatus") int httpStatus,
        @ForAll("optionalReceipt") String responseBody
    ) {
        // Given: A successful delivery result
        Message message = Message.builder()
            .messageId(messageId)
            .destinationUrl("https://example.com/webhook")
            .payload("{\"data\":\"test\"}")
            .build();
        
        DeliveryResult deliveryResult = DeliveryResult.builder()
            .messageId(messageId)
            .httpStatus(httpStatus)
            .responseBody(responseBody)
            .status(DeliveryStatus.SUCCESS)
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
        
        // Then: Message status should be DELIVERED
        ArgumentCaptor<MessageEntity> messageCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        verify(messageMapper).updateById(messageCaptor.capture());
        
        MessageEntity updatedMessage = messageCaptor.getValue();
        assertEquals("DELIVERED", updatedMessage.getStatus(), 
            "Message status should always be DELIVERED after successful delivery");
        assertNotNull(updatedMessage.getDeliveryTime(), 
            "Delivery timestamp should always be recorded");
        assertNotNull(updatedMessage.getUpdateTime(), 
            "Update timestamp should always be recorded");
    }
    
    // ========== Arbitraries (Generators) ==========
    
    @Provide
    Arbitrary<String> validMessageIds() {
        return Arbitraries.strings()
            .withCharRange('A', 'Z')
            .numeric()
            .withChars('-')
            .ofMinLength(5)
            .ofMaxLength(20)
            .map(s -> "MSG-" + s);
    }
    
    @Provide
    Arbitrary<String> validReceiptIds() {
        return Arbitraries.strings()
            .withCharRange('A', 'Z')
            .numeric()
            .withChars('-')
            .ofMinLength(5)
            .ofMaxLength(20)
            .map(s -> "R-" + s);
    }
    
    @Provide
    Arbitrary<Integer> successHttpStatus() {
        return Arbitraries.integers().between(200, 299);
    }
    
    @Provide
    Arbitrary<String> validTimestamps() {
        return Arbitraries.of(
            "2024-01-01T10:00:00",
            "2024-06-15T14:30:45",
            "2024-12-31T23:59:59"
        );
    }
    
    @Provide
    Arbitrary<String> jsonWithoutReceipt() {
        return Arbitraries.of(
            "{\"success\":true,\"message\":\"Processed successfully\"}",
            "{\"status\":\"ok\",\"data\":{\"id\":123}}",
            "{\"result\":\"completed\"}",
            "{}",
            "{\"success\":true}"
        );
    }
    
    @Provide
    Arbitrary<String> optionalReceipt() {
        return Arbitraries.oneOf(
            // With receipt
            Arbitraries.strings()
                .withCharRange('A', 'Z')
                .numeric()
                .ofMinLength(5)
                .ofMaxLength(10)
                .map(id -> String.format(
                    "{\"success\":true,\"receipt\":{\"receiptId\":\"R-%s\",\"timestamp\":\"2024-01-01T10:00:00\"}}",
                    id
                )),
            // Without receipt
            Arbitraries.of(
                "{\"success\":true,\"message\":\"Processed successfully\"}",
                "{\"status\":\"ok\"}"
            )
        );
    }
}
