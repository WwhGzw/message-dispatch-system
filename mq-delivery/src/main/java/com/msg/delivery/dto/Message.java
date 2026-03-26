package com.msg.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Message DTO
 * 
 * Data transfer object representing a message to be published to RabbitMQ.
 * Contains the essential information needed for message delivery.
 * 
 * @author MQ Delivery System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique message identifier
     */
    private String messageId;
    
    /**
     * Destination URL for HTTP delivery
     */
    private String destinationUrl;
    
    /**
     * Message payload (JSON format)
     */
    private String payload;
    
    /**
     * Current retry attempt count
     */
    private Integer retryCount;
    
    /**
     * Maximum retry attempts allowed
     */
    private Integer maxRetries;
}
