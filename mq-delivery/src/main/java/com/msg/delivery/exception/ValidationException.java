package com.msg.delivery.exception;

/**
 * Validation Exception
 * 
 * Thrown when message validation fails.
 * Contains specific error codes to indicate the validation failure reason.
 * 
 * @author MQ Delivery System
 */
public class ValidationException extends RuntimeException {
    
    /**
     * Error code indicating the specific validation failure
     */
    private final String errorCode;
    
    /**
     * Constructs a new ValidationException with the specified error code and message.
     * 
     * @param errorCode the error code indicating the validation failure reason
     * @param message the detail message
     */
    public ValidationException(String errorCode, String message) {
        super(message);
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
     * Error codes for validation failures
     */
    public static class ErrorCode {
        public static final String PAYLOAD_NULL = "PAYLOAD_NULL";
        public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
        public static final String MESSAGE_ID_NULL = "MESSAGE_ID_NULL";
        public static final String MESSAGE_ID_EMPTY = "MESSAGE_ID_EMPTY";
        public static final String DESTINATION_URL_NULL = "DESTINATION_URL_NULL";
        public static final String DESTINATION_URL_INVALID = "DESTINATION_URL_INVALID";
        public static final String DESTINATION_URL_TOO_LONG = "DESTINATION_URL_TOO_LONG";
    }
}
