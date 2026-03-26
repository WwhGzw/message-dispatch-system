package com.msg.delivery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Delivery Attempt Entity
 * 
 * Represents a single delivery attempt for a message, tracking the HTTP response,
 * delivery result, and timing information for each attempt.
 * 
 * @author MQ Delivery System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_mq_delivery_attempt")
public class DeliveryAttemptEntity {
    
    /**
     * Auto-increment primary key
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * Associated message identifier
     * References MessageEntity.messageId
     */
    private String messageId;
    
    /**
     * Attempt number (1-based)
     * First attempt = 1, second attempt = 2, etc.
     */
    private Integer attemptNumber;
    
    /**
     * HTTP status code returned by downstream channel
     * Nullable - only populated for successful HTTP requests
     */
    private Integer httpStatus;
    
    /**
     * Response body from downstream channel
     * Stored as TEXT type in database
     * Nullable - may be empty for timeouts or connection errors
     */
    private String responseBody;
    
    /**
     * Delivery result
     * Valid values: SUCCESS, TIMEOUT, CONNECTION_ERROR, HTTP_ERROR
     */
    private String deliveryResult;
    
    /**
     * Error message if delivery failed
     * Nullable - only populated when delivery fails
     */
    private String errorMessage;
    
    /**
     * Attempt timestamp
     * Records when this delivery attempt was made
     */
    private LocalDateTime attemptTime;
    
    /**
     * Delivery latency in milliseconds
     * Time from request start to response received
     * Nullable - not available for connection errors
     */
    private Long latencyMs;
}
