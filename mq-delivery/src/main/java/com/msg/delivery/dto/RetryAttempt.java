package com.msg.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Retry Attempt DTO
 * 
 * Data transfer object representing a single retry attempt for a message.
 * Contains attempt details including result, error information, and timing.
 * 
 * @author MQ Delivery System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryAttempt implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Attempt number (1-based)
     */
    private Integer attemptNumber;
    
    /**
     * HTTP status code returned (if applicable)
     */
    private Integer httpStatus;
    
    /**
     * Delivery result (SUCCESS, TIMEOUT, CONNECTION_ERROR, HTTP_ERROR)
     */
    private String deliveryResult;
    
    /**
     * Error message if delivery failed
     */
    private String errorMessage;
    
    /**
     * Attempt timestamp
     */
    private LocalDateTime attemptTime;
    
    /**
     * Delivery latency in milliseconds
     */
    private Long latencyMs;
}
