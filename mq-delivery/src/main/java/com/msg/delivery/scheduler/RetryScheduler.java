package com.msg.delivery.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.msg.delivery.dto.Message;
import com.msg.delivery.dto.RetryAttempt;
import com.msg.delivery.entity.DeliveryAttemptEntity;
import com.msg.delivery.entity.MessageEntity;
import com.msg.delivery.mapper.DeliveryAttemptMapper;
import com.msg.delivery.mapper.MessageMapper;
import com.msg.delivery.publisher.RabbitMQPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Retry Scheduler Component
 * 
 * Manages exponential backoff retry logic for failed message deliveries.
 * Implements automatic retry scheduling with exponentially increasing delays
 * and moves messages to dead letter queue after maximum retry attempts.
 * 
 * Responsibilities:
 * - Calculate exponential backoff delays using formula: min(1 * 2^(n-1), 300)
 * - Schedule retry attempts with appropriate delays
 * - Republish messages to main queue for retry (attempts < 5)
 * - Move messages to dead letter queue after max retries (attempts >= 5)
 * - Record retry history in database
 * 
 * @author MQ Delivery System
 */
@Slf4j
@Component
public class RetryScheduler {
    
    private static final int INITIAL_DELAY_SECONDS = 1;
    private static final int MAX_DELAY_SECONDS = 300;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    
    @Autowired
    private RabbitMQPublisher rabbitMQPublisher;
    
    @Autowired
    private MessageMapper messageMapper;
    
    @Autowired
    private DeliveryAttemptMapper deliveryAttemptMapper;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    
    /**
     * Calculate exponential backoff delay
     * 
     * Implements the formula: delay = min(initialDelay * 2^(attemptNumber-1), maxDelay)
     * 
     * Examples:
     *   - Attempt 1: min(1 * 2^0, 300) = 1 second
     *   - Attempt 2: min(1 * 2^1, 300) = 2 seconds
     *   - Attempt 3: min(1 * 2^2, 300) = 4 seconds
     *   - Attempt 4: min(1 * 2^3, 300) = 8 seconds
     *   - Attempt 5: min(1 * 2^4, 300) = 16 seconds
     * 
     * @param attemptNumber Current attempt number (1-based)
     * @return Delay in seconds before next retry
     * 
     * Preconditions:
     *   - attemptNumber >= 1
     * 
     * Postconditions:
     *   - Returns delay >= INITIAL_DELAY_SECONDS
     *   - Returns delay <= MAX_DELAY_SECONDS
     *   - Delay increases exponentially with attempt number
     */
    public int calculateBackoffDelay(int attemptNumber) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Attempt number must be >= 1");
        }
        
        // Calculate exponential delay: initialDelay * 2^(attemptNumber-1)
        int delay = INITIAL_DELAY_SECONDS * (int) Math.pow(2, attemptNumber - 1);
        
        // Cap at maximum delay
        int cappedDelay = Math.min(delay, MAX_DELAY_SECONDS);
        
        log.debug("Calculated backoff delay for attempt {}: {} seconds", attemptNumber, cappedDelay);
        
        return cappedDelay;
    }
    
    /**
     * Schedule retry for failed delivery
     * 
     * Implements retry logic with exponential backoff:
     * - For attempts < 5: calculate delay and republish to main queue after delay
     * - For attempts >= 5: publish to DLQ and update status to DEAD_LETTER
     * - Records retry history in database
     * 
     * @param message Message that failed delivery
     * @param attemptNumber Current attempt number (1-based)
     * @param failureReason Reason for delivery failure
     * 
     * Preconditions:
     *   - message is not null
     *   - attemptNumber >= 1
     *   - failureReason is not null
     * 
     * Postconditions:
     *   - If attemptNumber < 5: message republished after delay
     *   - If attemptNumber >= 5: message moved to DLQ, status updated to DEAD_LETTER
     *   - Retry history recorded in database
     */
    public void scheduleRetry(Message message, int attemptNumber, String failureReason) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("Attempt number must be >= 1");
        }
        if (failureReason == null || failureReason.isEmpty()) {
            throw new IllegalArgumentException("Failure reason cannot be null or empty");
        }
        
        log.info("Scheduling retry for message: messageId={}, attempt={}, reason={}", 
            message.getMessageId(), attemptNumber, failureReason);
        
        // Record the failed attempt in database
        recordRetryAttempt(message.getMessageId(), attemptNumber, failureReason);
        
        if (attemptNumber < MAX_RETRY_ATTEMPTS) {
            // Calculate backoff delay and schedule retry
            int delaySeconds = calculateBackoffDelay(attemptNumber);
            
            log.info("Scheduling retry after {} seconds: messageId={}, attempt={}", 
                delaySeconds, message.getMessageId(), attemptNumber);
            
            // Update message retry count
            updateMessageRetryCount(message.getMessageId(), attemptNumber);
            
            // Schedule delayed republish to main queue
            scheduler.schedule(() -> {
                try {
                    // Increment retry count for next attempt
                    message.setRetryCount(attemptNumber);
                    
                    log.info("Republishing message to main queue: messageId={}, nextAttempt={}", 
                        message.getMessageId(), attemptNumber + 1);
                    
                    rabbitMQPublisher.publishToMainQueue(message);
                    
                } catch (Exception e) {
                    log.error("Failed to republish message to main queue: messageId={}", 
                        message.getMessageId(), e);
                }
            }, delaySeconds, TimeUnit.SECONDS);
            
        } else {
            // Max retries exceeded - move to dead letter queue
            log.warn("Max retry attempts exceeded, moving to DLQ: messageId={}, attempts={}", 
                message.getMessageId(), attemptNumber);
            
            // Retrieve retry history for DLQ metadata
            List<RetryAttempt> retryHistory = getRetryHistory(message.getMessageId());
            
            // Publish to dead letter queue
            rabbitMQPublisher.publishToDeadLetterQueue(message, failureReason, retryHistory);
            
            // Update message status to DEAD_LETTER
            updateMessageStatusToDeadLetter(message.getMessageId(), failureReason);
            
            log.info("Message moved to dead letter queue: messageId={}, totalAttempts={}", 
                message.getMessageId(), attemptNumber);
        }
    }
    
    /**
     * Record retry attempt in database
     * 
     * @param messageId Message identifier
     * @param attemptNumber Attempt number
     * @param failureReason Failure reason
     */
    private void recordRetryAttempt(String messageId, int attemptNumber, String failureReason) {
        try {
            DeliveryAttemptEntity attempt = DeliveryAttemptEntity.builder()
                .messageId(messageId)
                .attemptNumber(attemptNumber)
                .deliveryResult("RETRY_SCHEDULED")
                .errorMessage(failureReason)
                .attemptTime(LocalDateTime.now())
                .build();
            
            deliveryAttemptMapper.insert(attempt);
            
            log.debug("Recorded retry attempt: messageId={}, attempt={}", messageId, attemptNumber);
            
        } catch (Exception e) {
            log.error("Failed to record retry attempt: messageId={}, attempt={}", 
                messageId, attemptNumber, e);
        }
    }
    
    /**
     * Update message retry count in database
     * 
     * @param messageId Message identifier
     * @param retryCount Current retry count
     */
    private void updateMessageRetryCount(String messageId, int retryCount) {
        try {
            LambdaUpdateWrapper<MessageEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(MessageEntity::getMessageId, messageId)
                .set(MessageEntity::getRetryCount, retryCount)
                .set(MessageEntity::getUpdateTime, LocalDateTime.now());
            
            messageMapper.update(null, updateWrapper);
            
            log.debug("Updated message retry count: messageId={}, retryCount={}", 
                messageId, retryCount);
            
        } catch (Exception e) {
            log.error("Failed to update message retry count: messageId={}", messageId, e);
        }
    }
    
    /**
     * Update message status to DEAD_LETTER in database
     * 
     * @param messageId Message identifier
     * @param failureReason Failure reason
     */
    private void updateMessageStatusToDeadLetter(String messageId, String failureReason) {
        try {
            LambdaUpdateWrapper<MessageEntity> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(MessageEntity::getMessageId, messageId)
                .set(MessageEntity::getStatus, "DEAD_LETTER")
                .set(MessageEntity::getFailureReason, failureReason)
                .set(MessageEntity::getUpdateTime, LocalDateTime.now());
            
            messageMapper.update(null, updateWrapper);
            
            log.info("Updated message status to DEAD_LETTER: messageId={}", messageId);
            
        } catch (Exception e) {
            log.error("Failed to update message status to DEAD_LETTER: messageId={}", 
                messageId, e);
        }
    }
    
    /**
     * Retrieve retry history for a message
     * 
     * @param messageId Message identifier
     * @return List of retry attempts
     */
    private List<RetryAttempt> getRetryHistory(String messageId) {
        List<RetryAttempt> retryHistory = new ArrayList<>();
        
        try {
            List<DeliveryAttemptEntity> attempts = deliveryAttemptMapper.selectList(
                new LambdaUpdateWrapper<DeliveryAttemptEntity>()
                    .eq(DeliveryAttemptEntity::getMessageId, messageId)
            );
            
            for (DeliveryAttemptEntity attempt : attempts) {
                RetryAttempt retryAttempt = RetryAttempt.builder()
                    .attemptNumber(attempt.getAttemptNumber())
                    .httpStatus(attempt.getHttpStatus())
                    .deliveryResult(attempt.getDeliveryResult())
                    .errorMessage(attempt.getErrorMessage())
                    .attemptTime(attempt.getAttemptTime())
                    .latencyMs(attempt.getLatencyMs())
                    .build();
                
                retryHistory.add(retryAttempt);
            }
            
            log.debug("Retrieved retry history: messageId={}, attempts={}", 
                messageId, retryHistory.size());
            
        } catch (Exception e) {
            log.error("Failed to retrieve retry history: messageId={}", messageId, e);
        }
        
        return retryHistory;
    }
    
    /**
     * Shutdown the scheduler gracefully
     */
    public void shutdown() {
        log.info("Shutting down retry scheduler...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Retry scheduler shutdown complete");
    }
}
