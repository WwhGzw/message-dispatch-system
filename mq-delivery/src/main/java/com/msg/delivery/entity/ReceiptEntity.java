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
 * Receipt Entity
 * 
 * Represents a delivery receipt from a downstream channel, tracking whether
 * the receipt has been consumed by the upstream system.
 * 
 * @author MQ Delivery System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_mq_receipt")
public class ReceiptEntity {
    
    /**
     * Auto-increment primary key
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * Original message identifier
     * References MessageEntity.messageId
     */
    private String messageId;
    
    /**
     * Receipt data from downstream channel (JSON format)
     * Stored as TEXT type in database
     */
    private String receiptData;
    
    /**
     * Receipt creation timestamp
     * Automatically set on insert
     */
    private LocalDateTime createTime;
    
    /**
     * Whether receipt has been consumed by upstream system
     * Default: false
     */
    private Boolean consumed;
    
    /**
     * Receipt consumption timestamp
     * Nullable - only set when consumed becomes true
     */
    private LocalDateTime consumeTime;
}
