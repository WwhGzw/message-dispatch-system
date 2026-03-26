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
 * Message Entity
 * 
 * Represents a message in the MQ delivery system with all tracking information
 * including delivery status, retry count, and timestamps.
 * 
 * @author MQ Delivery System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_mq_message")
public class MessageEntity {
    
    /**
     * Unique message identifier (primary key)
     * Uses ASSIGN_ID strategy for distributed ID generation
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String messageId;
    
    /**
     * Destination URL for HTTP delivery
     * Maximum length: 2048 characters
     */
    private String destinationUrl;
    
    /**
     * Message payload (JSON format)
     * Stored as TEXT type in database
     */
    private String payload;
    
    /**
     * Message status
     * Valid values: PENDING, DELIVERED, FAILED, DEAD_LETTER
     */
    private String status;
    
    /**
     * Current retry attempt count
     * Default: 0
     */
    private Integer retryCount;
    
    /**
     * Maximum retry attempts allowed
     * Default: 5
     */
    private Integer maxRetries;
    
    /**
     * Failure reason (if failed)
     * Nullable - only populated when delivery fails
     */
    private String failureReason;
    
    /**
     * Message creation timestamp
     * Automatically set on insert
     */
    private LocalDateTime createTime;
    
    /**
     * Last update timestamp
     * Automatically updated on modification
     */
    private LocalDateTime updateTime;
    
    /**
     * Delivery completion timestamp
     * Nullable - only set when status becomes DELIVERED
     */
    private LocalDateTime deliveryTime;
}
