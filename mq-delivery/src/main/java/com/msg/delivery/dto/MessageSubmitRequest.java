package com.msg.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * Message Submit Request DTO
 * 
 * Request object for submitting messages via Dubbo RPC interface.
 * Contains all required information for message submission.
 * 
 * @author MQ Delivery System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSubmitRequest implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique message identifier
     * Required, cannot be null or empty
     */
    private String messageId;
    
    /**
     * Destination URL for HTTP delivery
     * Required, must be valid HTTP/HTTPS URL
     * Maximum length: 2048 characters
     */
    private String destinationUrl;
    
    /**
     * Message payload (JSON format)
     * Required, cannot be null
     * Maximum size: 1MB (1048576 bytes)
     */
    private String payload;
    
    /**
     * Optional custom headers for HTTP delivery
     */
    private Map<String, String> headers;
}
