package com.msg.delivery.exception;

/**
 * Queue Publish Exception
 * 
 * Thrown when message publishing to RabbitMQ fails.
 * This exception indicates a failure in the message queue layer,
 * typically due to connection issues or broker unavailability.
 * 
 * @author MQ Delivery System
 */
public class QueuePublishException extends RuntimeException {
    
    /**
     * Constructs a new QueuePublishException with the specified detail message.
     * 
     * @param message the detail message
     */
    public QueuePublishException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new QueuePublishException with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public QueuePublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
