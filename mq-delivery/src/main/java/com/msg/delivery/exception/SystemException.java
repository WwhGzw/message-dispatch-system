package com.msg.delivery.exception;

/**
 * System Exception
 * 
 * Thrown when system-level errors occur (database, message queue, etc.).
 * Indicates infrastructure failures that prevent message processing.
 * 
 * @author MQ Delivery System
 */
public class SystemException extends RuntimeException {
    
    /**
     * Error code indicating the specific system failure
     */
    private final String errorCode;
    
    /**
     * Constructs a new SystemException with the specified error code and message.
     * 
     * @param errorCode the error code indicating the system failure reason
     * @param message the detail message
     */
    public SystemException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * Constructs a new SystemException with the specified error code, message, and cause.
     * 
     * @param errorCode the error code indicating the system failure reason
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public SystemException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    /**
     * Gets the error code.
     * 
     * @return the error code
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * Error codes for system failures
     */
    public static class ErrorCode {
        public static final String DB_UNAVAILABLE = "DB_UNAVAILABLE";
        public static final String MQ_UNAVAILABLE = "MQ_UNAVAILABLE";
        public static final String REDIS_UNAVAILABLE = "REDIS_UNAVAILABLE";
        public static final String PERSISTENCE_FAILED = "PERSISTENCE_FAILED";
        public static final String QUEUE_PUBLISH_FAILED = "QUEUE_PUBLISH_FAILED";
    }
}
