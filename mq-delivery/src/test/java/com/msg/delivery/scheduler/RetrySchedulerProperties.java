package com.msg.delivery.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.msg.delivery.dto.Message;
import com.msg.delivery.dto.RetryAttempt;
import com.msg.delivery.entity.DeliveryAttemptEntity;
import com.msg.delivery.entity.MessageEntity;
import com.msg.delivery.mapper.DeliveryAttemptMapper;
import com.msg.delivery.mapper.MessageMapper;
import com.msg.delivery.publisher.RabbitMQPublisher;
import net.jqwik.api.*;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-Based Tests for RetryScheduler
 * 
 * Tests universal properties that should hold across all valid inputs
 * for the retry scheduling logic and exponential backoff calculation.
 * 
 * @author MQ Delivery System
 */
class RetrySchedulerProperties {
    
    private RabbitMQPublisher rabbitMQPublisher;
    private MessageMapper messageMapper;
    private DeliveryAttemptMapper deliveryAttemptMapper;
    private RetryScheduler retryScheduler;
    
    @BeforeEach
    void setUp() {
        // Create fresh mocks for each test
        rabbitMQPublisher = Mockito.mock(RabbitMQPublisher.class);
        messageMapper = Mockito.mock(MessageMapper.class);
        deliveryAttemptMapper = Mockito.mock(DeliveryAttemptMapper.class);
        
        // Create RetryScheduler with mocked dependencies
        retryScheduler = new RetryScheduler();
        // Use reflection to inject mocks (since @InjectMocks doesn't work with @Property)
        try {
            java.lang.reflect.Field publisherField = RetryScheduler.class.getDeclaredField("rabbitMQPublisher");
            publisherField.setAccessible(true);
            publisherField.set(retryScheduler, rabbitMQPublisher);
            
            java.lang.reflect.Field messageMapperField = RetryScheduler.class.getDeclaredField("messageMapper");
            messageMapperField.setAccessible(true);
            messageMapperField.set(retryScheduler, messageMapper);
            
            java.lang.reflect.Field attemptMapperField = RetryScheduler.class.getDeclaredField("deliveryAttemptMapper");
            attemptMapperField.setAccessible(true);
            attemptMapperField.set(retryScheduler, deliveryAttemptMapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocks", e);
        }
    }
    
    /**
     * Property 9: Exponential Backoff Calculation
     * 
     * For any retry attempt number n (where 1 ≤ n ≤ 5), the backoff delay should equal
     * min(1 * 2^(n-1), 300) seconds, ensuring exponential growth capped at maximum delay.
     * 
     * **Validates: Requirements 5.2, 5.3**
     */
    @Property(tries = 100)
    @Label("Property 9: Exponential Backoff Calculation - Delay follows formula min(1 * 2^(n-1), 300)")
    void exponentialBackoffCalculationProperty(
        @ForAll @IntRange(min = 1, max = 20) int attemptNumber
    ) {
        // Act
        int actualDelay = retryScheduler.calculateBackoffDelay(attemptNumber);
        
        // Calculate expected delay using formula: min(1 * 2^(n-1), 300)
        int expectedDelay = Math.min((int) Math.pow(2, attemptNumber - 1), 300);
        
        // Assert
        assertEquals(expectedDelay, actualDelay,
            String.format("For attempt %d, expected delay %d but got %d",
                attemptNumber, expectedDelay, actualDelay));
        
        // Additional assertions
        assertTrue(actualDelay >= 1, "Delay should be at least 1 second");
        assertTrue(actualDelay <= 300, "Delay should not exceed 300 seconds");
        
        // Verify exponential growth for attempts within range
        if (attemptNumber < 10) {
            assertEquals((int) Math.pow(2, attemptNumber - 1), actualDelay,
                "Delay should grow exponentially for attempts < 10");
        } else {
            assertEquals(300, actualDelay,
                "Delay should be capped at 300 seconds for attempts >= 10");
        }
    }
    
    /**
     * Property 7: Retry Scheduling for Failures (Part 1 - Retry Logic)
     * 
     * For any message that fails delivery with attempt < 5, the system should schedule
     * a retry with exponentially increasing delay and should not move to DLQ.
     * 
     * **Validates: Requirements 5.1, 5.3, 5.5, 5.6**
     */
    @Property(tries = 100)
    @Label("Property 7: Retry Scheduling - Messages with attempts < 5 should be scheduled for retry")
    void retrySchedulingForFailuresProperty(
        @ForAll("validMessages") Message message,
        @ForAll @IntRange(min = 1, max = 4) int attemptNumber,
        @ForAll("failureReasons") String failureReason
    ) {
        // Arrange
        reset(rabbitMQPublisher, messageMapper, deliveryAttemptMapper);
        
        // Act
        retryScheduler.scheduleRetry(message, attemptNumber, failureReason);
        
        // Assert - verify retry attempt recorded
        ArgumentCaptor<DeliveryAttemptEntity> attemptCaptor = ArgumentCaptor.forClass(DeliveryAttemptEntity.class);
        verify(deliveryAttemptMapper).insert(attemptCaptor.capture());
        
        DeliveryAttemptEntity recordedAttempt = attemptCaptor.getValue();
        assertEquals(message.getMessageId(), recordedAttempt.getMessageId());
        assertEquals(attemptNumber, recordedAttempt.getAttemptNumber());
        assertEquals("RETRY_SCHEDULED", recordedAttempt.getDeliveryResult());
        assertEquals(failureReason, recordedAttempt.getErrorMessage());
        assertNotNull(recordedAttempt.getAttemptTime());
        
        // Assert - verify retry count updated
        verify(messageMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        
        // Assert - verify message NOT moved to DLQ (attempt < 5)
        verify(rabbitMQPublisher, never()).publishToDeadLetterQueue(any(), any(), any());
    }
    
    /**
     * Property 8: Dead Letter Queue After Max Retries
     * 
     * For any message that fails delivery after 5 or more retry attempts, the system
     * should publish the message to the dead letter queue, update the message status
     * to DEAD_LETTER, and NOT republish to main queue.
     * 
     * **Validates: Requirements 5.4, 6.1, 6.2, 6.3, 6.5**
     */
    @Property(tries = 100)
    @Label("Property 8: Dead Letter Queue - Messages with attempts >= 5 should move to DLQ")
    void deadLetterQueueAfterMaxRetriesProperty(
        @ForAll("validMessages") Message message,
        @ForAll @IntRange(min = 5, max = 10) int attemptNumber,
        @ForAll("failureReasons") String failureReason
    ) {
        // Arrange
        reset(rabbitMQPublisher, messageMapper, deliveryAttemptMapper);
        
        // Mock retry history
        List<DeliveryAttemptEntity> mockAttempts = new ArrayList<>();
        for (int i = 1; i <= attemptNumber; i++) {
            mockAttempts.add(DeliveryAttemptEntity.builder()
                .messageId(message.getMessageId())
                .attemptNumber(i)
                .deliveryResult("HTTP_ERROR")
                .errorMessage("HTTP 500")
                .attemptTime(LocalDateTime.now())
                .build());
        }
        when(deliveryAttemptMapper.selectList(any(LambdaUpdateWrapper.class)))
            .thenReturn(mockAttempts);
        
        // Act
        retryScheduler.scheduleRetry(message, attemptNumber, failureReason);
        
        // Assert - verify retry attempt recorded
        verify(deliveryAttemptMapper).insert(any(DeliveryAttemptEntity.class));
        
        // Assert - verify message moved to DLQ
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<RetryAttempt>> historyCaptor = ArgumentCaptor.forClass(List.class);
        
        verify(rabbitMQPublisher).publishToDeadLetterQueue(
            messageCaptor.capture(),
            reasonCaptor.capture(),
            historyCaptor.capture()
        );
        
        assertEquals(message.getMessageId(), messageCaptor.getValue().getMessageId());
        assertEquals(failureReason, reasonCaptor.getValue());
        assertEquals(attemptNumber, historyCaptor.getValue().size());
        
        // Assert - verify message status updated to DEAD_LETTER
        verify(messageMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        
        // Assert - verify message NOT republished to main queue
        verify(rabbitMQPublisher, never()).publishToMainQueue(any(Message.class));
    }
    
    /**
     * Property: Backoff Delay Monotonicity
     * 
     * For any two attempt numbers n1 and n2 where n1 < n2 and both are within
     * the exponential growth range (< 10), the delay for n2 should be greater
     * than or equal to the delay for n1.
     */
    @Property(tries = 100)
    @Label("Backoff delay should be monotonically increasing")
    void backoffDelayMonotonicityProperty(
        @ForAll @IntRange(min = 1, max = 9) int attempt1,
        @ForAll @IntRange(min = 1, max = 9) int attempt2
    ) {
        Assume.that(attempt1 < attempt2);
        
        // Act
        int delay1 = retryScheduler.calculateBackoffDelay(attempt1);
        int delay2 = retryScheduler.calculateBackoffDelay(attempt2);
        
        // Assert
        assertTrue(delay2 >= delay1,
            String.format("Delay for attempt %d (%d) should be >= delay for attempt %d (%d)",
                attempt2, delay2, attempt1, delay1));
    }
    
    /**
     * Property: Retry Attempt Recording Consistency
     * 
     * For any valid message and attempt number, scheduling a retry should always
     * record the attempt in the database with correct message ID and attempt number.
     */
    @Property(tries = 100)
    @Label("Retry attempt should always be recorded in database")
    void retryAttemptRecordingConsistencyProperty(
        @ForAll("validMessages") Message message,
        @ForAll @IntRange(min = 1, max = 10) int attemptNumber,
        @ForAll("failureReasons") String failureReason
    ) {
        // Arrange
        reset(rabbitMQPublisher, messageMapper, deliveryAttemptMapper);
        
        // Mock retry history for DLQ case
        when(deliveryAttemptMapper.selectList(any(LambdaUpdateWrapper.class)))
            .thenReturn(new ArrayList<>());
        
        // Act
        retryScheduler.scheduleRetry(message, attemptNumber, failureReason);
        
        // Assert - verify attempt recorded
        ArgumentCaptor<DeliveryAttemptEntity> attemptCaptor = ArgumentCaptor.forClass(DeliveryAttemptEntity.class);
        verify(deliveryAttemptMapper).insert(attemptCaptor.capture());
        
        DeliveryAttemptEntity recordedAttempt = attemptCaptor.getValue();
        assertEquals(message.getMessageId(), recordedAttempt.getMessageId(),
            "Recorded attempt should have correct message ID");
        assertEquals(attemptNumber, recordedAttempt.getAttemptNumber(),
            "Recorded attempt should have correct attempt number");
        assertEquals(failureReason, recordedAttempt.getErrorMessage(),
            "Recorded attempt should have correct failure reason");
        assertNotNull(recordedAttempt.getAttemptTime(),
            "Recorded attempt should have timestamp");
    }
    
    // ========== Arbitraries (Data Generators) ==========
    
    @Provide
    Arbitrary<Message> validMessages() {
        Arbitrary<String> messageIds = Arbitraries.strings()
            .alpha().numeric().ofMinLength(10).ofMaxLength(64);
        
        Arbitrary<String> urls = Arbitraries.of(
            "https://example.com/webhook",
            "https://api.example.com/callback",
            "http://localhost:8080/receive",
            "https://test.com/api/messages"
        );
        
        Arbitrary<String> payloads = Arbitraries.strings()
            .ofMinLength(10).ofMaxLength(1000)
            .map(s -> "{\"data\":\"" + s + "\"}");
        
        return Combinators.combine(messageIds, urls, payloads)
            .as((id, url, payload) -> Message.builder()
                .messageId(id)
                .destinationUrl(url)
                .payload(payload)
                .retryCount(0)
                .maxRetries(5)
                .build());
    }
    
    @Provide
    Arbitrary<String> failureReasons() {
        return Arbitraries.of(
            "Connection timeout",
            "HTTP 500 Internal Server Error",
            "HTTP 503 Service Unavailable",
            "Read timeout",
            "Connection refused",
            "Network unreachable",
            "HTTP 502 Bad Gateway",
            "HTTP 504 Gateway Timeout"
        );
    }
}
