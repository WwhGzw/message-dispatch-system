package com.msg.delivery.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msg.delivery.dto.DeliveryResult;
import com.msg.delivery.dto.Message;
import com.msg.delivery.dto.Receipt;
import com.msg.delivery.entity.MessageEntity;
import com.msg.delivery.entity.ReceiptEntity;
import com.msg.delivery.mapper.MessageMapper;
import com.msg.delivery.mapper.ReceiptMapper;
import com.msg.delivery.publisher.RabbitMQPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Receipt Processor Component
 * 
 * Processes delivery receipts from downstream channel responses.
 * Extracts receipt data from HTTP response bodies, publishes receipts
 * to the receipt queue, and updates message status to DELIVERED.
 * 
 * Responsibilities:
 * - Extract receipt data from JSON response bodies
 * - Publish receipts to receipt queue with original message ID correlation
 * - Update message status to DELIVERED in database
 * - Record delivery timestamp
 * 
 * @author MQ Delivery System
 */
@Slf4j
@Component
public class ReceiptProcessor {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private RabbitMQPublisher rabbitMQPublisher;
    
    @Autowired
    private MessageMapper messageMapper;
    
    @Autowired
    private ReceiptMapper receiptMapper;
    
    /**
     * Extract receipt from HTTP response body
     * 
     * Parses the JSON response body and extracts receipt data if present.
     * Expected response format:
     * {
     *   "success": true,
     *   "receipt": {
     *     "receiptId": "...",
     *     "timestamp": "...",
     *     "data": { ... }
     *   }
     * }
     * 
     * @param responseBody HTTP response body (JSON format)
     * @return Optional<Receipt> containing receipt if present, empty otherwise
     * 
     * Preconditions:
     *   - responseBody is not null
     * 
     * Postconditions:
     *   - Returns Optional.of(receipt) if receipt present and valid JSON
     *   - Returns Optional.empty() if receipt not present or invalid JSON
     *   - Does not throw exception on parse error
     */
    public Optional<Receipt> extractReceipt(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            log.debug("Response body is null or empty, no receipt to extract");
            return Optional.empty();
        }
        
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            
            if (root.has("receipt")) {
                JsonNode receiptNode = root.get("receipt");
                
                Receipt receipt = Receipt.builder()
                    .receiptData(receiptNode.toString())
                    .receiptTime(LocalDateTime.now())
                    .build();
                
                log.debug("Successfully extracted receipt from response body");
                return Optional.of(receipt);
            }
            
            log.debug("No receipt field found in response body");
            return Optional.empty();
            
        } catch (Exception e) {
            log.warn("Failed to extract receipt from response body: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    /**
     * Process receipt from downstream response
     * 
     * Processes a delivery result by extracting the receipt, publishing it
     * to the receipt queue, updating message status to DELIVERED, and
     * recording the delivery timestamp.
     * 
     * Processing Flow:
     *   1. Extract receipt data from response body (JSON)
     *   2. Associate receipt with original message identifier
     *   3. Publish receipt to receipt queue
     *   4. Update message status to DELIVERED in database
     *   5. Record delivery timestamp
     * 
     * @param deliveryResult Delivery result containing HTTP response
     * @param originalMessage Original message that was delivered
     * 
     * Preconditions:
     *   - deliveryResult.httpStatus is 200-299
     *   - deliveryResult.responseBody is not null
     *   - originalMessage is not null
     * 
     * Postconditions:
     *   - Receipt published to receipt queue if present in response
     *   - Receipt persisted to database
     *   - Message status updated to DELIVERED
     *   - Delivery timestamp recorded
     */
    public void processReceipt(DeliveryResult deliveryResult, Message originalMessage) {
        if (deliveryResult == null) {
            throw new IllegalArgumentException("DeliveryResult cannot be null");
        }
        if (originalMessage == null) {
            throw new IllegalArgumentException("Original message cannot be null");
        }
        
        String messageId = originalMessage.getMessageId();
        log.info("Processing receipt for message: messageId={}", messageId);
        
        try {
            // Extract receipt from response body
            Optional<Receipt> receiptOpt = extractReceipt(deliveryResult.getResponseBody());
            
            if (receiptOpt.isPresent()) {
                Receipt receipt = receiptOpt.get();
                receipt.setMessageId(messageId);
                
                // Publish receipt to receipt queue
                log.debug("Publishing receipt to receipt queue: messageId={}", messageId);
                rabbitMQPublisher.publishToReceiptQueue(receipt, messageId);
                
                // Persist receipt to database
                ReceiptEntity receiptEntity = ReceiptEntity.builder()
                    .messageId(messageId)
                    .receiptData(receipt.getReceiptData())
                    .createTime(receipt.getReceiptTime())
                    .consumed(false)
                    .build();
                
                receiptMapper.insert(receiptEntity);
                log.info("Receipt persisted to database: messageId={}", messageId);
            } else {
                log.debug("No receipt found in response for messageId={}", messageId);
            }
            
            // Update message status to DELIVERED and record delivery timestamp
            LocalDateTime deliveryTime = LocalDateTime.now();
            
            MessageEntity messageEntity = messageMapper.selectById(messageId);
            if (messageEntity != null) {
                messageEntity.setStatus("DELIVERED");
                messageEntity.setDeliveryTime(deliveryTime);
                messageEntity.setUpdateTime(deliveryTime);
                
                messageMapper.updateById(messageEntity);
                log.info("Message status updated to DELIVERED: messageId={}, deliveryTime={}", 
                    messageId, deliveryTime);
            } else {
                log.warn("Message not found in database: messageId={}", messageId);
            }
            
        } catch (Exception e) {
            log.error("Failed to process receipt for messageId={}", messageId, e);
            throw new RuntimeException("Failed to process receipt: " + messageId, e);
        }
    }
}
