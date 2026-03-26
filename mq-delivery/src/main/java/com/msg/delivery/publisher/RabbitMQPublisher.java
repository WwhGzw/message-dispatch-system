package com.msg.delivery.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msg.delivery.dto.Message;
import com.msg.delivery.dto.Receipt;
import com.msg.delivery.dto.RetryAttempt;
import com.msg.delivery.exception.QueuePublishException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelatedPublisherConfirmsAndReturnsCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RabbitMQ Publisher Component
 * 
 * Provides methods to publish messages to various RabbitMQ queues with
 * persistent delivery mode and publisher confirms for reliability.
 * 
 * Responsibilities:
 * - Publish messages to main queue with persistent delivery
 * - Publish receipts to receipt queue
 * - Publish failed messages to dead letter queue with metadata
 * - Handle publish failures with QueuePublishException
 * 
 * @author MQ Delivery System
 */
@Slf4j
@Component
public class RabbitMQPublisher {
    
    private static final String MAIN_EXCHANGE = "mq.delivery.exchange.main";
    private static final String RECEIPT_EXCHANGE = "mq.delivery.exchange.receipt";
    private static final String DLQ_EXCHANGE = "mq.delivery.exchange.dlq";
    
    private static final String MAIN_ROUTING_KEY = "mq.delivery.routing.main";
    private static final String RECEIPT_ROUTING_KEY = "mq.delivery.routing.receipt";
    private static final String DLQ_ROUTING_KEY = "mq.delivery.routing.dlq";
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    /**
     * Publish message to main queue
     * 
     * Publishes a message to the main delivery queue with persistent delivery mode
     * and publisher confirms to ensure broker acknowledgment.
     * 
     * @param message Message to publish
     * @throws QueuePublishException if publish fails
     * 
     * Preconditions:
     *   - message is not null
     *   - RabbitMQ connection is available
     * 
     * Postconditions:
     *   - Message published with persistent delivery mode
     *   - Message published to durable queue
     *   - Publisher confirms enabled (broker acknowledgment received)
     */
    public void publishToMainQueue(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        try {
            log.debug("Publishing message to main queue: messageId={}", message.getMessageId());
            
            rabbitTemplate.convertAndSend(
                MAIN_EXCHANGE,
                MAIN_ROUTING_KEY,
                message,
                msg -> {
                    // Set persistent delivery mode
                    msg.getMessageProperties().setDeliveryMode(MessageProperties.DEFAULT_DELIVERY_MODE);
                    msg.getMessageProperties().setPersistent(true);
                    return msg;
                }
            );
            
            log.info("Successfully published message to main queue: messageId={}", message.getMessageId());
            
        } catch (Exception e) {
            log.error("Failed to publish message to main queue: messageId={}", message.getMessageId(), e);
            throw new QueuePublishException(
                "Failed to publish message to main queue: " + message.getMessageId(), 
                e
            );
        }
    }
    
    /**
     * Publish receipt to receipt queue
     * 
     * Publishes a delivery receipt to the receipt queue for upstream system consumption.
     * Includes the original message ID for correlation.
     * 
     * @param receipt Receipt data from downstream channel
     * @param originalMessageId Original message identifier for correlation
     * @throws QueuePublishException if publish fails
     * 
     * Preconditions:
     *   - receipt is not null
     *   - originalMessageId is not null
     *   - RabbitMQ connection is available
     * 
     * Postconditions:
     *   - Receipt published with persistent delivery mode
     *   - Receipt includes original message ID in headers
     */
    public void publishToReceiptQueue(Receipt receipt, String originalMessageId) {
        if (receipt == null) {
            throw new IllegalArgumentException("Receipt cannot be null");
        }
        if (originalMessageId == null || originalMessageId.isEmpty()) {
            throw new IllegalArgumentException("Original message ID cannot be null or empty");
        }
        
        try {
            log.debug("Publishing receipt to receipt queue: messageId={}", originalMessageId);
            
            rabbitTemplate.convertAndSend(
                RECEIPT_EXCHANGE,
                RECEIPT_ROUTING_KEY,
                receipt,
                msg -> {
                    // Set persistent delivery mode
                    msg.getMessageProperties().setPersistent(true);
                    // Add original message ID to headers for correlation
                    msg.getMessageProperties().setHeader("originalMessageId", originalMessageId);
                    return msg;
                }
            );
            
            log.info("Successfully published receipt to receipt queue: messageId={}", originalMessageId);
            
        } catch (Exception e) {
            log.error("Failed to publish receipt to receipt queue: messageId={}", originalMessageId, e);
            throw new QueuePublishException(
                "Failed to publish receipt to receipt queue: " + originalMessageId, 
                e
            );
        }
    }
    
    /**
     * Publish message to dead letter queue
     * 
     * Publishes a failed message to the dead letter queue with failure metadata
     * including failure reason and complete retry history.
     * 
     * @param message Message that failed after max retries
     * @param failureReason Reason for failure
     * @param retryHistory List of retry attempts with timestamps
     * @throws QueuePublishException if publish fails
     * 
     * Preconditions:
     *   - message is not null
     *   - failureReason is not null
     *   - retryHistory is not null
     *   - RabbitMQ connection is available
     * 
     * Postconditions:
     *   - Message published to DLQ with persistent delivery mode
     *   - Failure metadata included in message headers
     *   - Retry history serialized and included
     */
    public void publishToDeadLetterQueue(Message message, String failureReason, List<RetryAttempt> retryHistory) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (failureReason == null || failureReason.isEmpty()) {
            throw new IllegalArgumentException("Failure reason cannot be null or empty");
        }
        if (retryHistory == null) {
            throw new IllegalArgumentException("Retry history cannot be null");
        }
        
        try {
            log.debug("Publishing message to dead letter queue: messageId={}, reason={}", 
                message.getMessageId(), failureReason);
            
            rabbitTemplate.convertAndSend(
                DLQ_EXCHANGE,
                DLQ_ROUTING_KEY,
                message,
                msg -> {
                    // Set persistent delivery mode
                    msg.getMessageProperties().setPersistent(true);
                    
                    // Add failure metadata to headers
                    Map<String, Object> headers = new HashMap<>();
                    headers.put("failureReason", failureReason);
                    headers.put("retryCount", retryHistory.size());
                    
                    // Serialize retry history to JSON and add to headers
                    try {
                        String retryHistoryJson = objectMapper.writeValueAsString(retryHistory);
                        headers.put("retryHistory", retryHistoryJson);
                    } catch (Exception e) {
                        log.warn("Failed to serialize retry history for messageId={}", message.getMessageId(), e);
                        headers.put("retryHistory", "[]");
                    }
                    
                    msg.getMessageProperties().getHeaders().putAll(headers);
                    return msg;
                }
            );
            
            log.info("Successfully published message to dead letter queue: messageId={}, retryCount={}", 
                message.getMessageId(), retryHistory.size());
            
        } catch (Exception e) {
            log.error("Failed to publish message to dead letter queue: messageId={}", 
                message.getMessageId(), e);
            throw new QueuePublishException(
                "Failed to publish message to dead letter queue: " + message.getMessageId(), 
                e
            );
        }
    }
}
