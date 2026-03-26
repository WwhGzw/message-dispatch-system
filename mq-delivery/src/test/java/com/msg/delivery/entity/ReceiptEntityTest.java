package com.msg.delivery.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReceiptEntity
 */
class ReceiptEntityTest {

    @Test
    void testReceiptEntityBuilder() {
        // Given
        Long id = 1L;
        String messageId = "test-message-123";
        String receiptData = "{\"receiptId\":\"receipt-456\",\"status\":\"success\"}";
        LocalDateTime createTime = LocalDateTime.now();
        Boolean consumed = false;

        // When
        ReceiptEntity entity = ReceiptEntity.builder()
                .id(id)
                .messageId(messageId)
                .receiptData(receiptData)
                .createTime(createTime)
                .consumed(consumed)
                .build();

        // Then
        assertNotNull(entity);
        assertEquals(id, entity.getId());
        assertEquals(messageId, entity.getMessageId());
        assertEquals(receiptData, entity.getReceiptData());
        assertEquals(createTime, entity.getCreateTime());
        assertEquals(consumed, entity.getConsumed());
        assertNull(entity.getConsumeTime());
    }

    @Test
    void testReceiptEntitySettersAndGetters() {
        // Given
        ReceiptEntity entity = new ReceiptEntity();
        Long id = 2L;
        String messageId = "msg-789";
        String receiptData = "{\"receiptId\":\"receipt-123\",\"timestamp\":\"2024-01-01T10:00:00\"}";
        LocalDateTime createTime = LocalDateTime.now();
        Boolean consumed = true;
        LocalDateTime consumeTime = LocalDateTime.now();

        // When
        entity.setId(id);
        entity.setMessageId(messageId);
        entity.setReceiptData(receiptData);
        entity.setCreateTime(createTime);
        entity.setConsumed(consumed);
        entity.setConsumeTime(consumeTime);

        // Then
        assertEquals(id, entity.getId());
        assertEquals(messageId, entity.getMessageId());
        assertEquals(receiptData, entity.getReceiptData());
        assertEquals(createTime, entity.getCreateTime());
        assertEquals(consumed, entity.getConsumed());
        assertEquals(consumeTime, entity.getConsumeTime());
    }

    @Test
    void testReceiptEntityAllArgsConstructor() {
        // Given
        Long id = 3L;
        String messageId = "msg-456";
        String receiptData = "{\"receiptId\":\"receipt-789\",\"data\":{\"result\":\"ok\"}}";
        LocalDateTime createTime = LocalDateTime.now();
        Boolean consumed = true;
        LocalDateTime consumeTime = LocalDateTime.now();

        // When
        ReceiptEntity entity = new ReceiptEntity(
                id,
                messageId,
                receiptData,
                createTime,
                consumed,
                consumeTime
        );

        // Then
        assertNotNull(entity);
        assertEquals(id, entity.getId());
        assertEquals(messageId, entity.getMessageId());
        assertEquals(receiptData, entity.getReceiptData());
        assertEquals(createTime, entity.getCreateTime());
        assertEquals(consumed, entity.getConsumed());
        assertEquals(consumeTime, entity.getConsumeTime());
    }

    @Test
    void testReceiptEntityNoArgsConstructor() {
        // When
        ReceiptEntity entity = new ReceiptEntity();

        // Then
        assertNotNull(entity);
        assertNull(entity.getId());
        assertNull(entity.getMessageId());
        assertNull(entity.getReceiptData());
        assertNull(entity.getCreateTime());
        assertNull(entity.getConsumed());
        assertNull(entity.getConsumeTime());
    }

    @Test
    void testReceiptEntityWithUnconsumedReceipt() {
        // Given
        String messageId = "msg-unconsumed";
        String receiptData = "{\"receiptId\":\"receipt-unconsumed\"}";
        LocalDateTime createTime = LocalDateTime.now();

        // When
        ReceiptEntity entity = ReceiptEntity.builder()
                .messageId(messageId)
                .receiptData(receiptData)
                .createTime(createTime)
                .consumed(false)
                .build();

        // Then
        assertNotNull(entity);
        assertEquals(messageId, entity.getMessageId());
        assertEquals(receiptData, entity.getReceiptData());
        assertEquals(createTime, entity.getCreateTime());
        assertFalse(entity.getConsumed());
        assertNull(entity.getConsumeTime());
    }

    @Test
    void testReceiptEntityWithConsumedReceipt() {
        // Given
        String messageId = "msg-consumed";
        String receiptData = "{\"receiptId\":\"receipt-consumed\"}";
        LocalDateTime createTime = LocalDateTime.now();
        LocalDateTime consumeTime = LocalDateTime.now().plusMinutes(5);

        // When
        ReceiptEntity entity = ReceiptEntity.builder()
                .messageId(messageId)
                .receiptData(receiptData)
                .createTime(createTime)
                .consumed(true)
                .consumeTime(consumeTime)
                .build();

        // Then
        assertNotNull(entity);
        assertEquals(messageId, entity.getMessageId());
        assertEquals(receiptData, entity.getReceiptData());
        assertEquals(createTime, entity.getCreateTime());
        assertTrue(entity.getConsumed());
        assertEquals(consumeTime, entity.getConsumeTime());
    }
}
