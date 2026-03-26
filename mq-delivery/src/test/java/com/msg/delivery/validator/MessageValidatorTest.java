package com.msg.delivery.validator;

import com.msg.delivery.dto.Message;
import com.msg.delivery.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageValidator
 * 
 * Tests validation rules for message payloads including:
 * - Payload validation (null, size limits)
 * - Message ID validation (null, empty)
 * - Destination URL validation (null, format, length)
 */
class MessageValidatorTest {
    
    private MessageValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new MessageValidator();
    }
    
    @Test
    void testValidMessage() {
        Message message = Message.builder()
            .messageId("test-message-123")
            .destinationUrl("https://example.com/webhook")
            .payload("{\"data\":\"test\"}")
            .build();
        
        assertDoesNotThrow(() -> validator.validate(message));
    }
    
    @Test
    void testValidHttpUrl() {
        Message message = Message.builder()
            .messageId("test-message-123")
            .destinationUrl("http://example.com/webhook")
            .payload("{\"data\":\"test\"}")
            .build();
        
        assertDoesNotThrow(() -> validator.validate(message));
    }
    
    @Test
    void testNullPayload() {
        Message message = Message.builder()
            .messageId("test-message-123")
            .destinationUrl("https://example.com/webhook")
            .payload(null)
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.PAYLOAD_NULL, exception.getErrorCode());
        assertEquals("Payload cannot be null", exception.getMessage());
    }
    
    @Test
    void testPayloadTooLarge() {
        // Create a payload larger than 1MB
        StringBuilder largePayload = new StringBuilder();
        for (int i = 0; i < 1048577; i++) {
            largePayload.append("a");
        }
        
        Message message = Message.builder()
            .messageId("test-message-123")
            .destinationUrl("https://example.com/webhook")
            .payload(largePayload.toString())
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.PAYLOAD_TOO_LARGE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("exceeds maximum allowed size"));
    }
    
    @Test
    void testPayloadExactly1MB() {
        // Create a payload exactly 1MB
        StringBuilder payload = new StringBuilder();
        for (int i = 0; i < 1048576; i++) {
            payload.append("a");
        }
        
        Message message = Message.builder()
            .messageId("test-message-123")
            .destinationUrl("https://example.com/webhook")
            .payload(payload.toString())
            .build();
        
        assertDoesNotThrow(() -> validator.validate(message));
    }
    
    @Test
    void testNullMessageId() {
        Message message = Message.builder()
            .messageId(null)
            .destinationUrl("https://example.com/webhook")
            .payload("{\"data\":\"test\"}")
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.MESSAGE_ID_NULL, exception.getErrorCode());
        assertEquals("Message ID cannot be null", exception.getMessage());
    }
    
    @Test
    void testEmptyMessageId() {
        Message message = Message.builder()
            .messageId("")
            .destinationUrl("https://example.com/webhook")
            .payload("{\"data\":\"test\"}")
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.MESSAGE_ID_EMPTY, exception.getErrorCode());
        assertEquals("Message ID cannot be empty", exception.getMessage());
    }
    
    @Test
    void testWhitespaceOnlyMessageId() {
        Message message = Message.builder()
            .messageId("   ")
            .destinationUrl("https://example.com/webhook")
            .payload("{\"data\":\"test\"}")
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.MESSAGE_ID_EMPTY, exception.getErrorCode());
        assertEquals("Message ID cannot be empty", exception.getMessage());
    }
    
    @Test
    void testNullDestinationUrl() {
        Message message = Message.builder()
            .messageId("test-message-123")
            .destinationUrl(null)
            .payload("{\"data\":\"test\"}")
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.DESTINATION_URL_NULL, exception.getErrorCode());
        assertEquals("Destination URL cannot be null", exception.getMessage());
    }
    
    @Test
    void testInvalidDestinationUrlFormat() {
        Message message = Message.builder()
            .messageId("test-message-123")
            .destinationUrl("ftp://example.com/webhook")
            .payload("{\"data\":\"test\"}")
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.DESTINATION_URL_INVALID, exception.getErrorCode());
        assertEquals("Destination URL must be a valid HTTP or HTTPS URL", exception.getMessage());
    }
    
    @Test
    void testInvalidDestinationUrlNoProtocol() {
        Message message = Message.builder()
            .messageId("test-message-123")
            .destinationUrl("example.com/webhook")
            .payload("{\"data\":\"test\"}")
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.DESTINATION_URL_INVALID, exception.getErrorCode());
    }
    
    @Test
    void testDestinationUrlTooLong() {
        // Create a URL longer than 2048 characters
        StringBuilder longUrl = new StringBuilder("https://example.com/");
        for (int i = 0; i < 2050; i++) {
            longUrl.append("a");
        }
        
        Message message = Message.builder()
            .messageId("test-message-123")
            .destinationUrl(longUrl.toString())
            .payload("{\"data\":\"test\"}")
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.DESTINATION_URL_TOO_LONG, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("exceeds maximum allowed length"));
    }
    
    @Test
    void testDestinationUrlExactly2048Characters() {
        // Create a URL exactly 2048 characters
        StringBuilder url = new StringBuilder("https://example.com/");
        int remaining = 2048 - url.length();
        for (int i = 0; i < remaining; i++) {
            url.append("a");
        }
        
        Message message = Message.builder()
            .messageId("test-message-123")
            .destinationUrl(url.toString())
            .payload("{\"data\":\"test\"}")
            .build();
        
        assertDoesNotThrow(() -> validator.validate(message));
    }
}
