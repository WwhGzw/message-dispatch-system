package com.msg.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Delivery Result DTO
 * 
 * Data transfer object representing the result of a message delivery attempt.
 * Contains HTTP response details, delivery status, and extracted receipt.
 * 
 * @author MQ Delivery System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryResult implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Message identifier
     */
    private String messageId;
    
    /**
     * HTTP status code (null if connection error or timeout)
     */
    private Integer httpStatus;
    
    /**
     * HTTP response body
     */
    private String responseBody;
    
    /**
     * Delivery status (SUCCESS, TIMEOUT, CONNECTION_ERROR, HTTP_ERROR)
     */
    private DeliveryStatus status;
    
    /**
     * Error message if delivery failed
     */
    private String errorMessage;
    
    /**
     * Delivery latency in milliseconds
     */
    private Long latencyMs;
    
    /**
     * Extracted receipt from response (if present)
     */
    private Receipt receipt;
}
