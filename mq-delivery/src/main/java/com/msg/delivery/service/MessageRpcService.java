package com.msg.delivery.service;

import com.msg.delivery.dto.MessageSubmitRequest;
import com.msg.delivery.dto.MessageSubmitResponse;
import com.msg.delivery.exception.SystemException;
import com.msg.delivery.exception.ValidationException;

/**
 * Message RPC Service Interface
 * 
 * Dubbo RPC interface for accepting messages from upstream systems.
 * Provides message submission endpoint with validation, persistence, and queue publishing.
 * 
 * @author MQ Delivery System
 */
public interface MessageRpcService {
    
    /**
     * Submit message for delivery
     * 
     * Accepts a message from upstream system, validates the payload, persists to database,
     * and publishes to RabbitMQ main queue for delivery processing.
     * 
     * @param request Message submission request containing messageId, destinationUrl, and payload
     * @return MessageSubmitResponse containing unique message identifier and acceptance timestamp
     * @throws ValidationException if payload validation fails (null payload, size > 1MB, 
     *         invalid URL, missing required fields)
     * @throws SystemException if persistence or queue publish fails (database unavailable,
     *         RabbitMQ unavailable)
     * 
     * Preconditions:
     *   - request is not null
     *   - request.payload is not null and size <= 1MB
     *   - request.destinationUrl is valid HTTP/HTTPS URL
     *   - required fields (messageId, destinationUrl, payload) are present
     * 
     * Postconditions:
     *   - Message persisted to MySQL with status PENDING
     *   - Message published to RabbitMQ main queue
     *   - Returns unique message identifier within 100ms
     */
    MessageSubmitResponse submitMessage(MessageSubmitRequest request);
}
