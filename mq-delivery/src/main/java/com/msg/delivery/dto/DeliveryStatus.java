package com.msg.delivery.dto;

/**
 * Delivery Status Enum
 * 
 * Represents the outcome of a message delivery attempt.
 * 
 * @author MQ Delivery System
 */
public enum DeliveryStatus {
    
    /**
     * Delivery succeeded (HTTP 200-299)
     */
    SUCCESS,
    
    /**
     * Delivery timed out (connection or read timeout)
     */
    TIMEOUT,
    
    /**
     * Connection error (unable to connect to downstream)
     */
    CONNECTION_ERROR,
    
    /**
     * HTTP error (HTTP 400-599)
     */
    HTTP_ERROR
}
