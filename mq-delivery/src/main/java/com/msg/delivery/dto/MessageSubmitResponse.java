package com.msg.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Message Submit Response DTO
 * 
 * Response object returned after successful message submission.
 * Contains the unique message identifier and acceptance timestamp.
 * 
 * @author MQ Delivery System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSubmitResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique message identifier
     * Same as the messageId from the request
     */
    private String messageId;
    
    /**
     * Timestamp when the message was accepted
     */
    private LocalDateTime acceptedTime;
}
