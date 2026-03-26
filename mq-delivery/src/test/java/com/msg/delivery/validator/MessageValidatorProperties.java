package com.msg.delivery.validator;

import com.msg.delivery.dto.Message;
import com.msg.delivery.exception.ValidationException;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-Based Tests for MessageValidator
 * 
 * Uses jqwik to generate random test cases and verify validation properties
 * hold across all valid and invalid inputs.
 */
class MessageValidatorProperties {
    
    private final MessageValidator validator = new MessageValidator();
    
    /**
     * Property 2: Invalid Message Rejection
     * 
     * **Validates: Requirements 1.3, 1.4, 14.1, 14.2, 14.3, 14.4, 14.5**
     * 
     * For any invalid message submission request (null payload, payload > 1MB, 
     * missing required fields, or invalid destination URL), the validator should 
     * reject the request and throw ValidationException with a descriptive error code.
     */
    @Property(tries = 100)
    @Label("Property 2: Invalid Message Rejection - Null Payload")
    void invalidMessageWithNullPayloadShouldBeRejected(
        @ForAll @StringLength(min = 1, max = 100) String messageId,
        @ForAll("validHttpUrl") String destinationUrl
    ) {
        Message message = Message.builder()
            .messageId(messageId)
            .destinationUrl(destinationUrl)
            .payload(null)
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.PAYLOAD_NULL, exception.getErrorCode());
        assertNotNull(exception.getMessage());
    }
    
    @Property(tries = 100)
    @Label("Property 2: Invalid Message Rejection - Oversized Payload")
    void invalidMessageWithOversizedPayloadShouldBeRejected(
        @ForAll @StringLength(min = 1, max = 100) String messageId,
        @ForAll("validHttpUrl") String destinationUrl,
        @ForAll @IntRange(min = 1048577, max = 2000000) int payloadSize
    ) {
        // Create payload larger than 1MB
        StringBuilder payload = new StringBuilder(payloadSize);
        for (int i = 0; i < payloadSize; i++) {
            payload.append("a");
        }
        
        Message message = Message.builder()
            .messageId(messageId)
            .destinationUrl(destinationUrl)
            .payload(payload.toString())
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.PAYLOAD_TOO_LARGE, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("exceeds maximum allowed size"));
    }
    
    @Property(tries = 100)
    @Label("Property 2: Invalid Message Rejection - Null Message ID")
    void invalidMessageWithNullMessageIdShouldBeRejected(
        @ForAll("validHttpUrl") String destinationUrl,
        @ForAll @StringLength(min = 1, max = 1000) String payload
    ) {
        Message message = Message.builder()
            .messageId(null)
            .destinationUrl(destinationUrl)
            .payload(payload)
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.MESSAGE_ID_NULL, exception.getErrorCode());
        assertNotNull(exception.getMessage());
    }
    
    @Property(tries = 100)
    @Label("Property 2: Invalid Message Rejection - Empty Message ID")
    void invalidMessageWithEmptyMessageIdShouldBeRejected(
        @ForAll("validHttpUrl") String destinationUrl,
        @ForAll @StringLength(min = 1, max = 1000) String payload,
        @ForAll("whitespaceString") String emptyMessageId
    ) {
        Message message = Message.builder()
            .messageId(emptyMessageId)
            .destinationUrl(destinationUrl)
            .payload(payload)
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.MESSAGE_ID_EMPTY, exception.getErrorCode());
        assertNotNull(exception.getMessage());
    }
    
    @Property(tries = 100)
    @Label("Property 2: Invalid Message Rejection - Null Destination URL")
    void invalidMessageWithNullDestinationUrlShouldBeRejected(
        @ForAll @StringLength(min = 1, max = 100) String messageId,
        @ForAll @StringLength(min = 1, max = 1000) String payload
    ) {
        Message message = Message.builder()
            .messageId(messageId)
            .destinationUrl(null)
            .payload(payload)
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.DESTINATION_URL_NULL, exception.getErrorCode());
        assertNotNull(exception.getMessage());
    }
    
    @Property(tries = 100)
    @Label("Property 2: Invalid Message Rejection - Invalid URL Format")
    void invalidMessageWithInvalidUrlFormatShouldBeRejected(
        @ForAll @StringLength(min = 1, max = 100) String messageId,
        @ForAll @StringLength(min = 1, max = 1000) String payload,
        @ForAll("invalidUrl") String invalidUrl
    ) {
        Message message = Message.builder()
            .messageId(messageId)
            .destinationUrl(invalidUrl)
            .payload(payload)
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.DESTINATION_URL_INVALID, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("valid HTTP or HTTPS URL"));
    }
    
    @Property(tries = 100)
    @Label("Property 2: Invalid Message Rejection - URL Too Long")
    void invalidMessageWithUrlTooLongShouldBeRejected(
        @ForAll @StringLength(min = 1, max = 100) String messageId,
        @ForAll @StringLength(min = 1, max = 1000) String payload,
        @ForAll @IntRange(min = 2049, max = 3000) int urlLength
    ) {
        // Create URL longer than 2048 characters
        StringBuilder url = new StringBuilder("https://example.com/");
        int remaining = urlLength - url.length();
        for (int i = 0; i < remaining; i++) {
            url.append("a");
        }
        
        Message message = Message.builder()
            .messageId(messageId)
            .destinationUrl(url.toString())
            .payload(payload)
            .build();
        
        ValidationException exception = assertThrows(
            ValidationException.class,
            () -> validator.validate(message)
        );
        
        assertEquals(ValidationException.ErrorCode.DESTINATION_URL_TOO_LONG, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("exceeds maximum allowed length"));
    }
    
    /**
     * Arbitrary provider for valid HTTP/HTTPS URLs
     */
    @Provide
    Arbitrary<String> validHttpUrl() {
        Arbitrary<String> protocol = Arbitraries.of("http://", "https://");
        Arbitrary<String> domain = Arbitraries.strings()
            .alpha()
            .ofMinLength(3)
            .ofMaxLength(50)
            .map(s -> s + ".com");
        Arbitrary<String> path = Arbitraries.strings()
            .alpha()
            .numeric()
            .ofMinLength(0)
            .ofMaxLength(100)
            .map(s -> s.isEmpty() ? "" : "/" + s);
        
        return Combinators.combine(protocol, domain, path)
            .as((p, d, pa) -> p + d + pa);
    }
    
    /**
     * Arbitrary provider for invalid URLs (non-HTTP/HTTPS)
     */
    @Provide
    Arbitrary<String> invalidUrl() {
        return Arbitraries.oneOf(
            // URLs with invalid protocols
            Arbitraries.of("ftp://example.com", "file://example.com", "ws://example.com"),
            // URLs without protocol
            Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(50).map(s -> s + ".com"),
            // Random strings that don't look like URLs
            Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50)
        );
    }
    
    /**
     * Arbitrary provider for whitespace-only strings (empty after trim)
     */
    @Provide
    Arbitrary<String> whitespaceString() {
        return Arbitraries.integers().between(0, 10)
            .map(n -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    sb.append(" ");
                }
                return sb.toString();
            });
    }
}
