package com.msg.delivery.service.impl;

import com.msg.delivery.dto.Message;
import com.msg.delivery.dto.MessageSubmitRequest;
import com.msg.delivery.dto.MessageSubmitResponse;
import com.msg.delivery.entity.MessageEntity;
import com.msg.delivery.exception.QueuePublishException;
import com.msg.delivery.exception.SystemException;
import com.msg.delivery.exception.ValidationException;
import com.msg.delivery.mapper.MessageMapper;
import com.msg.delivery.publisher.RabbitMQPublisher;
import com.msg.delivery.service.MessageRpcService;
import com.msg.delivery.validator.MessageValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Message RPC Service Implementation
 * 
 * Dubbo RPC service implementation that accepts messages from upstream systems,
 * validates them, persists to database, and publishes to RabbitMQ.
 * 
 * Responsibilities:
 * - Validate message payload using MessageValidator
 * - Persist message to MySQL with status PENDING
 * - Publish message to RabbitMQ main queue
 * - Return unique message identifier within 100ms
 * - Handle ValidationException and SystemException
 * 
 * @author MQ Delivery System
 */
@Slf4j
@DubboService(version = "1.0.0", timeout = 3000)
public class MessageRpcServiceImpl implements MessageRpcService {
    
    @Autowired
    private MessageValidator messageValidator;
    
    @Autowired
    private MessageMapper messageMapper;
    
    @Autowired
    private RabbitMQPublisher rabbitMQPublisher;
    
    @Value("${mq.retry.max-attempts:5}")
    private int maxRetries;
    
    /**
     * Submit message for delivery
     * 
     * Validates the request, persists to database with PENDING status,
     * publishes to RabbitMQ main queue, and returns message identifier.
     * 
     * @param request Message submission request
     * @return MessageSubmitResponse containing message ID and acceptance time
     * @throws ValidationException if validation fails
     * @throws SystemException if persistence or queue publish fails
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageSubmitResponse submitMessage(MessageSubmitRequest request) {
        long startTime = System.currentTimeMillis();
        
        log.info("Received message submission request: messageId={}", request.getMessageId());
        
        try {
            // Step 1: Convert request to Message DTO for validation
            Message message = convertToMessage(request);
            
            // Step 2: Validate message payload
            messageValidator.validate(message);
            log.debug("Message validation passed: messageId={}", request.getMessageId());
            
            // Step 3: Persist message to database with status PENDING
            MessageEntity entity = convertToEntity(request);
            try {
                messageMapper.insert(entity);
                log.debug("Message persisted to database: messageId={}", request.getMessageId());
            } catch (DataAccessException e) {
                log.error("Failed to persist message to database: messageId={}", 
                    request.getMessageId(), e);
                throw new SystemException(
                    SystemException.ErrorCode.DB_UNAVAILABLE,
                    "Failed to persist message to database: " + request.getMessageId(),
                    e
                );
            }
            
            // Step 4: Publish message to RabbitMQ main queue
            try {
                rabbitMQPublisher.publishToMainQueue(message);
                log.debug("Message published to main queue: messageId={}", request.getMessageId());
            } catch (QueuePublishException e) {
                log.error("Failed to publish message to main queue: messageId={}", 
                    request.getMessageId(), e);
                throw new SystemException(
                    SystemException.ErrorCode.MQ_UNAVAILABLE,
                    "Failed to publish message to main queue: " + request.getMessageId(),
                    e
                );
            }
            
            // Step 5: Build and return response
            LocalDateTime acceptedTime = LocalDateTime.now();
            MessageSubmitResponse response = MessageSubmitResponse.builder()
                .messageId(request.getMessageId())
                .acceptedTime(acceptedTime)
                .build();
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("Message submission completed: messageId={}, elapsedTime={}ms", 
                request.getMessageId(), elapsedTime);
            
            return response;
            
        } catch (ValidationException e) {
            log.warn("Message validation failed: messageId={}, errorCode={}, message={}", 
                request.getMessageId(), e.getErrorCode(), e.getMessage());
            throw e;
        } catch (SystemException e) {
            log.error("System error during message submission: messageId={}, errorCode={}", 
                request.getMessageId(), e.getErrorCode(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during message submission: messageId={}", 
                request.getMessageId(), e);
            throw new SystemException(
                SystemException.ErrorCode.PERSISTENCE_FAILED,
                "Unexpected error during message submission: " + request.getMessageId(),
                e
            );
        }
    }
    
    /**
     * Convert MessageSubmitRequest to Message DTO
     * 
     * @param request Message submission request
     * @return Message DTO for validation and queue publishing
     */
    private Message convertToMessage(MessageSubmitRequest request) {
        return Message.builder()
            .messageId(request.getMessageId())
            .destinationUrl(request.getDestinationUrl())
            .payload(request.getPayload())
            .retryCount(0)
            .maxRetries(maxRetries)
            .build();
    }
    
    /**
     * Convert MessageSubmitRequest to MessageEntity
     * 
     * @param request Message submission request
     * @return MessageEntity for database persistence
     */
    private MessageEntity convertToEntity(MessageSubmitRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return MessageEntity.builder()
            .messageId(request.getMessageId())
            .destinationUrl(request.getDestinationUrl())
            .payload(request.getPayload())
            .status("PENDING")
            .retryCount(0)
            .maxRetries(maxRetries)
            .createTime(now)
            .updateTime(now)
            .build();
    }
}
