package com.msg.delivery.scheduler;

import com.msg.delivery.dto.Message;
import com.msg.delivery.entity.DeliveryAttemptEntity;
import com.msg.delivery.entity.MessageEntity;
import com.msg.delivery.mapper.DeliveryAttemptMapper;
import com.msg.delivery.mapper.MessageMapper;
import com.msg.delivery.publisher.RabbitMQPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for RetryScheduler
 * 
 * Tests the complete retry flow including database operations,
 * message publishing, and scheduled task execution.
 * 
 * @author MQ Delivery System
 */
@SpringBootTest
@ActiveProfiles("test")
class RetrySchedulerIntegrationTest {
    
    @Autowired
    private RetryScheduler retryScheduler;
    
    @MockBean
    private RabbitMQPublisher rabbitMQPublisher;
    
    @MockBean
    private MessageMapper messageMapper;
    
    @MockBean
    private DeliveryAttemptMapper deliveryAttemptMapper;
    
    private Message testMessage;
    
    @BeforeEach
    void setUp() {
        testMessage = Message.builder()
            .messageId("integration-test-message-456")
            .destinationUrl("https://integration-test.com/webhook")
            .payload("{\"test\":\"integration\"}")
            .retryCount(0)
            .maxRetries(5)
            .build();
        
        // Reset mocks
        reset(rabbitMQPublisher, messageMapper, deliveryAttemptMapper);
    }
    
    @Test
    void testCompleteRetryFlow_Attempt1_SchedulesRetryAfter1Second() throws InterruptedException {
        // Arrange
        String failureReason = "Integration test - connection timeout";
        
        // Act
        retryScheduler.scheduleRetry(testMessage, 1, failureReason);
        
        // Assert - verify immediate actions
        verify(deliveryAttemptMapper, times(1)).insert(any(DeliveryAttemptEntity.class));
        verify(messageMapper, times(1)).update(eq(null), any());
        
        // Wait for scheduled task (1 second + buffer)
        TimeUnit.SECONDS.sleep(2);
        
        // Assert - verify message republished after delay
        verify(rabbitMQPublisher, timeout(1000).times(1)).publishToMainQueue(any(Message.class));
        verify(rabbitMQPublisher, never()).publishToDeadLetterQueue(any(), any(), any());
    }
    
    @Test
    void testCompleteRetryFlow_Attempt3_SchedulesRetryAfter4Seconds() throws InterruptedException {
        // Arrange
        String failureReason = "Integration test - HTTP 500 error";
        
        // Act
        retryScheduler.scheduleRetry(testMessage, 3, failureReason);
        
        // Assert - verify immediate actions
        verify(deliveryAttemptMapper, times(1)).insert(any(DeliveryAttemptEntity.class));
        verify(messageMapper, times(1)).update(eq(null), any());
        
        // Verify message NOT republished immediately
        verify(rabbitMQPublisher, never()).publishToMainQueue(any(Message.class));
        
        // Wait for scheduled task (4 seconds + buffer)
        TimeUnit.SECONDS.sleep(5);
        
        // Assert - verify message republished after delay
        verify(rabbitMQPublisher, timeout(1000).times(1)).publishToMainQueue(any(Message.class));
    }
    
    @Test
    void testCompleteRetryFlow_Attempt5_MovesToDLQ() {
        // Arrange
        String failureReason = "Integration test - max retries exceeded";
        
        // Mock retry history
        when(deliveryAttemptMapper.selectList(any()))
            .thenReturn(java.util.Arrays.asList(
                createMockAttempt(1),
                createMockAttempt(2),
                createMockAttempt(3),
                createMockAttempt(4),
                createMockAttempt(5)
            ));
        
        // Act
        retryScheduler.scheduleRetry(testMessage, 5, failureReason);
        
        // Assert - verify immediate actions
        verify(deliveryAttemptMapper, times(1)).insert(any(DeliveryAttemptEntity.class));
        verify(messageMapper, times(1)).update(eq(null), any());
        
        // Assert - verify message moved to DLQ
        verify(rabbitMQPublisher, times(1)).publishToDeadLetterQueue(
            any(Message.class),
            eq(failureReason),
            any()
        );
        
        // Assert - verify message NOT republished to main queue
        verify(rabbitMQPublisher, never()).publishToMainQueue(any(Message.class));
    }
    
    @Test
    void testExponentialBackoffProgression() {
        // Test the complete exponential backoff sequence
        assertEquals(1, retryScheduler.calculateBackoffDelay(1));
        assertEquals(2, retryScheduler.calculateBackoffDelay(2));
        assertEquals(4, retryScheduler.calculateBackoffDelay(3));
        assertEquals(8, retryScheduler.calculateBackoffDelay(4));
        assertEquals(16, retryScheduler.calculateBackoffDelay(5));
        assertEquals(32, retryScheduler.calculateBackoffDelay(6));
        assertEquals(64, retryScheduler.calculateBackoffDelay(7));
        assertEquals(128, retryScheduler.calculateBackoffDelay(8));
        assertEquals(256, retryScheduler.calculateBackoffDelay(9));
        assertEquals(300, retryScheduler.calculateBackoffDelay(10)); // Capped at 300
        assertEquals(300, retryScheduler.calculateBackoffDelay(15)); // Still capped
    }
    
    @Test
    void testMultipleRetriesInSequence() throws InterruptedException {
        // Simulate a sequence of retries for the same message
        
        // Attempt 1
        retryScheduler.scheduleRetry(testMessage, 1, "First failure");
        verify(deliveryAttemptMapper, times(1)).insert(any(DeliveryAttemptEntity.class));
        
        // Wait for first retry
        TimeUnit.SECONDS.sleep(2);
        verify(rabbitMQPublisher, times(1)).publishToMainQueue(any(Message.class));
        
        // Attempt 2
        reset(deliveryAttemptMapper, messageMapper);
        retryScheduler.scheduleRetry(testMessage, 2, "Second failure");
        verify(deliveryAttemptMapper, times(1)).insert(any(DeliveryAttemptEntity.class));
        
        // Wait for second retry (2 seconds)
        TimeUnit.SECONDS.sleep(3);
        verify(rabbitMQPublisher, times(2)).publishToMainQueue(any(Message.class));
    }
    
    @Test
    void testDatabaseFailureHandling() {
        // Arrange - simulate database failure
        doThrow(new RuntimeException("Database connection lost"))
            .when(deliveryAttemptMapper).insert(any(DeliveryAttemptEntity.class));
        
        // Act & Assert - should not throw exception
        assertDoesNotThrow(() -> {
            retryScheduler.scheduleRetry(testMessage, 1, "Test failure");
        });
        
        // Verify error was logged but processing continued
        verify(deliveryAttemptMapper, times(1)).insert(any(DeliveryAttemptEntity.class));
    }
    
    @Test
    void testPublishFailureHandling() throws InterruptedException {
        // Arrange - simulate publish failure
        doThrow(new RuntimeException("RabbitMQ connection lost"))
            .when(rabbitMQPublisher).publishToMainQueue(any(Message.class));
        
        // Act
        retryScheduler.scheduleRetry(testMessage, 1, "Test failure");
        
        // Wait for scheduled task
        TimeUnit.SECONDS.sleep(2);
        
        // Assert - verify publish was attempted
        verify(rabbitMQPublisher, timeout(1000).times(1)).publishToMainQueue(any(Message.class));
    }
    
    // Helper method to create mock delivery attempt
    private DeliveryAttemptEntity createMockAttempt(int attemptNumber) {
        return DeliveryAttemptEntity.builder()
            .messageId(testMessage.getMessageId())
            .attemptNumber(attemptNumber)
            .deliveryResult("HTTP_ERROR")
            .errorMessage("HTTP 500")
            .attemptTime(LocalDateTime.now())
            .httpStatus(500)
            .latencyMs(1000L)
            .build();
    }
}
