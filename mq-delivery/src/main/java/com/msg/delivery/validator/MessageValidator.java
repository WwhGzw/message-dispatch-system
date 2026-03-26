package com.msg.delivery.validator;

import com.msg.delivery.dto.Message;
import com.msg.delivery.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Message Validator
 * 
 * Validates message payloads before acceptance into the system.
 * Ensures messages meet all requirements for size, format, and content.
 * 
 * @author MQ Delivery System
 */
@Component
public class MessageValidator {
    
    /**
     * Maximum payload size: 1MB (1048576 bytes)
     */
    private static final int MAX_PAYLOAD_SIZE = 1048576;
    
    /**
     * Maximum destination URL length: 2048 characters
     */
    private static final int MAX_URL_LENGTH = 2048;
    
    /**
     * HTTP/HTTPS URL pattern
     */
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*");
    
    /**
     * Validate message submission request
     * 
     * @param message Message to validate
     * @throws ValidationException with specific error code if validation fails
     * 
     * Validation Rules:
     *   - payload not null
     *   - payload size <= 1MB (1048576 bytes)
     *   - messageId not null and not empty
     *   - destinationUrl not null and matches HTTP/HTTPS URL pattern
     *   - destinationUrl length <= 2048 characters
     */
    public void validate(Message message) {
        validatePayload(message.getPayload());
        validateMessageId(message.getMessageId());
        validateDestinationUrl(message.getDestinationUrl());
    }
    
    /**
     * Validate payload
     * 
     * @param payload Message payload
     * @throws ValidationException if payload is null or exceeds size limit
     */
    private void validatePayload(String payload) {
        if (payload == null) {
            throw new ValidationException(
                ValidationException.ErrorCode.PAYLOAD_NULL,
                "Payload cannot be null"
            );
        }
        
        // Calculate byte size using UTF-8 encoding
        int payloadSize = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (payloadSize > MAX_PAYLOAD_SIZE) {
            throw new ValidationException(
                ValidationException.ErrorCode.PAYLOAD_TOO_LARGE,
                String.format("Payload size %d bytes exceeds maximum allowed size of %d bytes", 
                    payloadSize, MAX_PAYLOAD_SIZE)
            );
        }
    }
    
    /**
     * Validate message identifier
     * 
     * @param messageId Message identifier
     * @throws ValidationException if messageId is null or empty
     */
    private void validateMessageId(String messageId) {
        if (messageId == null) {
            throw new ValidationException(
                ValidationException.ErrorCode.MESSAGE_ID_NULL,
                "Message ID cannot be null"
            );
        }
        
        if (messageId.trim().isEmpty()) {
            throw new ValidationException(
                ValidationException.ErrorCode.MESSAGE_ID_EMPTY,
                "Message ID cannot be empty"
            );
        }
    }
    
    /**
     * Validate destination URL
     * 
     * @param destinationUrl Destination URL
     * @throws ValidationException if URL is null, invalid format, or too long
     */
    private void validateDestinationUrl(String destinationUrl) {
        if (destinationUrl == null) {
            throw new ValidationException(
                ValidationException.ErrorCode.DESTINATION_URL_NULL,
                "Destination URL cannot be null"
            );
        }
        
        if (!URL_PATTERN.matcher(destinationUrl).matches()) {
            throw new ValidationException(
                ValidationException.ErrorCode.DESTINATION_URL_INVALID,
                "Destination URL must be a valid HTTP or HTTPS URL"
            );
        }
        
        if (destinationUrl.length() > MAX_URL_LENGTH) {
            throw new ValidationException(
                ValidationException.ErrorCode.DESTINATION_URL_TOO_LONG,
                String.format("Destination URL length %d exceeds maximum allowed length of %d characters",
                    destinationUrl.length(), MAX_URL_LENGTH)
            );
        }
    }
}
