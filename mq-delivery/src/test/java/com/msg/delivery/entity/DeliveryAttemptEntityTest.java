package com.msg.delivery.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DeliveryAttemptEntity
 * 
 * Validates entity construction, builder pattern, and field access.
 */
class DeliveryAttemptEntityTest {

    @Test
    void testBuilderPattern() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        // When
        DeliveryAttemptEntity entity = DeliveryAttemptEntity.builder()
                .id(1L)
                .messageId("test-message-123")
                .attemptNumber(1)
                .httpStatus(200)
                .responseBody("{\"success\":true}")
                .deliveryResult("SUCCESS")
                .errorMessage(null)
                .attemptTime(now)
                .latencyMs(150L)
                .build();
        
        // Then
        assertNotNull(entity);
        assertEquals(1L, entity.getId());
        assertEquals("test-message-123", entity.getMessageId());
        assertEquals(1, entity.getAttemptNumber());
        assertEquals(200, entity.getHttpStatus());
        assertEquals("{\"success\":true}", entity.getResponseBody());
        assertEquals("SUCCESS", entity.getDeliveryResult());
        assertNull(entity.getErrorMessage());
        assertEquals(now, entity.getAttemptTime());
        assertEquals(150L, entity.getLatencyMs());
    }

    @Test
    void testNoArgsConstructor() {
        // When
        DeliveryAttemptEntity entity = new DeliveryAttemptEntity();
        
        // Then
        assertNotNull(entity);
        assertNull(entity.getId());
        assertNull(entity.getMessageId());
    }

    @Test
    void testAllArgsConstructor() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        
        // When
        DeliveryAttemptEntity entity = new DeliveryAttemptEntity(
                1L,
                "test-message-456",
                2,
                500,
                "{\"error\":\"Internal Server Error\"}",
                "HTTP_ERROR",
                "Server returned 500",
                now,
                200L
        );
        
        // Then
        assertNotNull(entity);
        assertEquals(1L, entity.getId());
        assertEquals("test-message-456", entity.getMessageId());
        assertEquals(2, entity.getAttemptNumber());
        assertEquals(500, entity.getHttpStatus());
        assertEquals("{\"error\":\"Internal Server Error\"}", entity.getResponseBody());
        assertEquals("HTTP_ERROR", entity.getDeliveryResult());
        assertEquals("Server returned 500", entity.getErrorMessage());
        assertEquals(now, entity.getAttemptTime());
        assertEquals(200L, entity.getLatencyMs());
    }

    @Test
    void testSettersAndGetters() {
        // Given
        DeliveryAttemptEntity entity = new DeliveryAttemptEntity();
        LocalDateTime now = LocalDateTime.now();
        
        // When
        entity.setId(10L);
        entity.setMessageId("msg-789");
        entity.setAttemptNumber(3);
        entity.setHttpStatus(null);
        entity.setResponseBody(null);
        entity.setDeliveryResult("TIMEOUT");
        entity.setErrorMessage("Connection timeout after 30 seconds");
        entity.setAttemptTime(now);
        entity.setLatencyMs(null);
        
        // Then
        assertEquals(10L, entity.getId());
        assertEquals("msg-789", entity.getMessageId());
        assertEquals(3, entity.getAttemptNumber());
        assertNull(entity.getHttpStatus());
        assertNull(entity.getResponseBody());
        assertEquals("TIMEOUT", entity.getDeliveryResult());
        assertEquals("Connection timeout after 30 seconds", entity.getErrorMessage());
        assertEquals(now, entity.getAttemptTime());
        assertNull(entity.getLatencyMs());
    }

    @Test
    void testTimeoutScenario() {
        // Given - Simulating a timeout scenario where HTTP status and latency are null
        LocalDateTime attemptTime = LocalDateTime.now();
        
        // When
        DeliveryAttemptEntity entity = DeliveryAttemptEntity.builder()
                .messageId("timeout-msg-001")
                .attemptNumber(1)
                .httpStatus(null)
                .responseBody(null)
                .deliveryResult("TIMEOUT")
                .errorMessage("Read timeout after 30 seconds")
                .attemptTime(attemptTime)
                .latencyMs(null)
                .build();
        
        // Then
        assertEquals("TIMEOUT", entity.getDeliveryResult());
        assertNull(entity.getHttpStatus());
        assertNull(entity.getLatencyMs());
        assertNotNull(entity.getErrorMessage());
    }

    @Test
    void testConnectionErrorScenario() {
        // Given - Simulating a connection error scenario
        LocalDateTime attemptTime = LocalDateTime.now();
        
        // When
        DeliveryAttemptEntity entity = DeliveryAttemptEntity.builder()
                .messageId("conn-error-msg-002")
                .attemptNumber(2)
                .httpStatus(null)
                .responseBody(null)
                .deliveryResult("CONNECTION_ERROR")
                .errorMessage("Connection refused")
                .attemptTime(attemptTime)
                .latencyMs(null)
                .build();
        
        // Then
        assertEquals("CONNECTION_ERROR", entity.getDeliveryResult());
        assertNull(entity.getHttpStatus());
        assertNull(entity.getResponseBody());
        assertEquals("Connection refused", entity.getErrorMessage());
    }

    @Test
    void testSuccessfulDeliveryScenario() {
        // Given - Simulating a successful delivery
        LocalDateTime attemptTime = LocalDateTime.now();
        
        // When
        DeliveryAttemptEntity entity = DeliveryAttemptEntity.builder()
                .messageId("success-msg-003")
                .attemptNumber(1)
                .httpStatus(200)
                .responseBody("{\"status\":\"received\",\"receipt\":{\"id\":\"rcpt-123\"}}")
                .deliveryResult("SUCCESS")
                .errorMessage(null)
                .attemptTime(attemptTime)
                .latencyMs(125L)
                .build();
        
        // Then
        assertEquals("SUCCESS", entity.getDeliveryResult());
        assertEquals(200, entity.getHttpStatus());
        assertNotNull(entity.getResponseBody());
        assertNull(entity.getErrorMessage());
        assertEquals(125L, entity.getLatencyMs());
    }
}
