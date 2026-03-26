package com.msg.delivery.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageEntity
 */
class MessageEntityTest {

    @Test
    void testMessageEntityBuilder() {
        // Given
        String messageId = "test-message-123";
        String destinationUrl = "https://example.com/webhook";
        String payload = "{\"data\":\"test\"}";
        String status = "PENDING";
        Integer retryCount = 0;
        Integer maxRetries = 5;
        LocalDateTime now = LocalDateTime.now();

        // When
        MessageEntity entity = MessageEntity.builder()
                .messageId(messageId)
                .destinationUrl(destinationUrl)
                .payload(payload)
                .status(status)
                .retryCount(retryCount)
                .maxRetries(maxRetries)
                .createTime(now)
                .updateTime(now)
                .build();

        // Then
        assertNotNull(entity);
        assertEquals(messageId, entity.getMessageId());
        assertEquals(destinationUrl, entity.getDestinationUrl());
        assertEquals(payload, entity.getPayload());
        assertEquals(status, entity.getStatus());
        assertEquals(retryCount, entity.getRetryCount());
        assertEquals(maxRetries, entity.getMaxRetries());
        assertEquals(now, entity.getCreateTime());
        assertEquals(now, entity.getUpdateTime());
        assertNull(entity.getFailureReason());
        assertNull(entity.getDeliveryTime());
    }

    @Test
    void testMessageEntitySettersAndGetters() {
        // Given
        MessageEntity entity = new MessageEntity();
        String messageId = "msg-456";
        String destinationUrl = "https://api.example.com/callback";
        String payload = "{\"event\":\"test\"}";
        String status = "DELIVERED";
        Integer retryCount = 2;
        Integer maxRetries = 5;
        String failureReason = "Connection timeout";
        LocalDateTime createTime = LocalDateTime.now();
        LocalDateTime updateTime = LocalDateTime.now();
        LocalDateTime deliveryTime = LocalDateTime.now();

        // When
        entity.setMessageId(messageId);
        entity.setDestinationUrl(destinationUrl);
        entity.setPayload(payload);
        entity.setStatus(status);
        entity.setRetryCount(retryCount);
        entity.setMaxRetries(maxRetries);
        entity.setFailureReason(failureReason);
        entity.setCreateTime(createTime);
        entity.setUpdateTime(updateTime);
        entity.setDeliveryTime(deliveryTime);

        // Then
        assertEquals(messageId, entity.getMessageId());
        assertEquals(destinationUrl, entity.getDestinationUrl());
        assertEquals(payload, entity.getPayload());
        assertEquals(status, entity.getStatus());
        assertEquals(retryCount, entity.getRetryCount());
        assertEquals(maxRetries, entity.getMaxRetries());
        assertEquals(failureReason, entity.getFailureReason());
        assertEquals(createTime, entity.getCreateTime());
        assertEquals(updateTime, entity.getUpdateTime());
        assertEquals(deliveryTime, entity.getDeliveryTime());
    }

    @Test
    void testMessageEntityAllArgsConstructor() {
        // Given
        String messageId = "msg-789";
        String destinationUrl = "https://webhook.example.com";
        String payload = "{\"type\":\"notification\"}";
        String status = "FAILED";
        Integer retryCount = 5;
        Integer maxRetries = 5;
        String failureReason = "Max retries exceeded";
        LocalDateTime createTime = LocalDateTime.now();
        LocalDateTime updateTime = LocalDateTime.now();
        LocalDateTime deliveryTime = null;

        // When
        MessageEntity entity = new MessageEntity(
                messageId,
                destinationUrl,
                payload,
                status,
                retryCount,
                maxRetries,
                failureReason,
                createTime,
                updateTime,
                deliveryTime
        );

        // Then
        assertNotNull(entity);
        assertEquals(messageId, entity.getMessageId());
        assertEquals(destinationUrl, entity.getDestinationUrl());
        assertEquals(payload, entity.getPayload());
        assertEquals(status, entity.getStatus());
        assertEquals(retryCount, entity.getRetryCount());
        assertEquals(maxRetries, entity.getMaxRetries());
        assertEquals(failureReason, entity.getFailureReason());
        assertEquals(createTime, entity.getCreateTime());
        assertEquals(updateTime, entity.getUpdateTime());
        assertNull(entity.getDeliveryTime());
    }

    @Test
    void testMessageEntityNoArgsConstructor() {
        // When
        MessageEntity entity = new MessageEntity();

        // Then
        assertNotNull(entity);
        assertNull(entity.getMessageId());
        assertNull(entity.getDestinationUrl());
        assertNull(entity.getPayload());
        assertNull(entity.getStatus());
        assertNull(entity.getRetryCount());
        assertNull(entity.getMaxRetries());
        assertNull(entity.getFailureReason());
        assertNull(entity.getCreateTime());
        assertNull(entity.getUpdateTime());
        assertNull(entity.getDeliveryTime());
    }
}
