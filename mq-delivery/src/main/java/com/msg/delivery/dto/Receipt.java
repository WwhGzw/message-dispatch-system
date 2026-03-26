package com.msg.delivery.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Receipt DTO
 * 
 * Data transfer object representing a delivery receipt from a downstream channel.
 * Contains receipt data and timing information.
 * 
 * @author MQ Delivery System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Receipt implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Original message identifier
     */
    private String messageId;
    
    /**
     * Receipt data from downstream channel (JSON format)
     */
    private String receiptData;
    
    /**
     * Receipt timestamp
     */
    private LocalDateTime receiptTime;
}
