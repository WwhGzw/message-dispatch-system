package com.msg.delivery.scheduler;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.msg.delivery.dto.Message;
import com.msg.delivery.dto.RetryAttempt;
import com.msg.delivery.entity.DeliveryAttemptEntity;
import com.msg.delivery.entity.MessageEntity;
import com.msg.delivery.mapper.DeliveryAttemptMapper;
import com.msg.delivery.mapper.MessageMapper;
import com.msg.delivery.publisher.RabbitMQPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RetryScheduler
 * 
 * Tests the exponential backoff calculation, retry scheduling logic,
 * and dead letter queue handling.
 * 
 * @author MQ Delivery System
 */
@ExtendWith(MockitoExtension.class)
class RetrySchedulerTest {
    
    @Mock
    private RabbitMQPublisher rabbitMQPublisher;
    
    @Mock
    private MessageMapper messageMapper;
    
    @Mock
    private DeliveryAttemptMapper deliveryAttemptMapper;
    
    @InjectMocks
    private RetryScheduler retryScheduler;
    
    private Message testMessage;
    
    @BeforeEach
    void setUp() {
        testMessage = Message.builder()
            .messageId("test-message-123")
            .destinationUrl("https://example.com/webhook")
            .payload("{\"data\":\"test\"}")
            .retryCount(0)
            .maxRetries(5)
            .build();
    }
    
    @Test
    void testCalculateBackoffDelay_Attempt1() {
        // Attempt 1: min(1 * 2^0, 300) = 1 second
        int delay = retryScheduler.calculateBackoffDelay(1);
        assertEquals(1, delay);
    }
    
    @Test
    void testCalculateBackoffDelay_Attempt2() {
        // Attempt 2: min(1 * 2^1, 300) = 2 seconds
        int delay = retryScheduler.calculateBackoffDelay(2);
        assertEquals(2, delay);
    }
    
    @Test
    void testCalculateBackoffDelay_Attempt3() {
        // Attempt 3: min(1 * 2^2, 300) = 4 seconds
        int delay = retryScheduler.calculateBackoffDelay(3);
        assertEquals(4, delay);
    }
    
    @Test
    void testCalculateBackoffDelay_Attempt4() {
        // Attempt 4: min(1 * 2^3, 300) = 8 seconds
        int delay = retryScheduler.calculateBackoffDelay(4);
        assertEquals(8, delay);
    }
    
    @Test
    void testCalculateBackoffDelay_Attempt5() {
        // Attempt 5: min(1 * 2^4, 300) = 16 seconds
        int delay = retryScheduler.calculateBackoffDelay(5);
        assertEquals(16, delay);
    }
    
    @Test
    void testCalculateBackoffDelay_Attempt10_CappedAtMax() {
        // Attempt 10: min(1 * 2^9, 300) = min(512, 300) = 300 seconds (capped)
        int delay = retryScheduler.calculateBackoffDelay(10);
        assertEquals(300, delay);
    }
    
    @Test
    void testCalculateBackoffDelay_InvalidAttemptNumber() {
        // Attempt number < 1 should throw exception
        assertThrows(IllegalArgumentException.class, () -> {
            retryScheduler.calculateBackoffDelay(0);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            retryScheduler.calculateBackoffDelay(-1);
        });
    }
    
    @Test
    void testScheduleRetry_Attempt1_SchedulesRetry() throws InterruptedException {
        // Arrange
        String failureReason = "Connection timeout";
        
        // Act
        retryScheduler.scheduleRetry(testMessage, 1, failureReason);
        
        // Assert - verify retry attempt recorded
        ArgumentCaptor<DeliveryAttemptEntity> attemptCaptor = ArgumentCaptor.forClass(DeliveryAttemptEntity.class);
        verify(deliveryAttemptMapper).insert(attemptCaptor.capture());
        
        DeliveryAttemptEntity recordedAttempt = attemptCaptor.getValue();
        assertEquals("test-message-123", recordedAttempt.getMessageId());
        assertEquals(1, recordedAttempt.getAttemptNumber());
        assertEquals("RETRY_SCHEDULED", recordedAttempt.getDeliveryResult());
        assertEquals(failureReason, recordedAttempt.getErrorMessage());
        assertNotNull(recordedAttempt.getAttemptTime());
        
        // Assert - verify retry count updated
        verify(messageMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        
        // Wait for scheduled task to execute (1 second delay + buffer)
        TimeUnit.SECONDS.sleep(2);
        
        // Assert - verify message republished to main queue
        verify(rabbitMQPublisher, timeout(3000)).publishToMainQueue(any(Message.class));
    }
    
    @Test
    void testScheduleRetry_Attempt4_SchedulesRetry() throws InterruptedException {
        // Arrange
        String failureReason = "HTTP 500 error";
        
        // Act
        retryScheduler.scheduleRetry(testMessage, 4, failureReason);
        
        // Assert - verify retry attempt recorded
        verify(deliveryAttemptMapper).insert(any(DeliveryAttemptEntity.class));
        
        // Assert - verify retry count updated
        verify(messageMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        
        // Verify message NOT moved to DLQ (attempt < 5)
        verify(rabbitMQPublisher, never()).publishToDeadLetterQueue(any(), any(), any());
    }
    
    @Test
    void testScheduleRetry_Attempt5_MovesToDLQ() {
        // Arrange
        String failureReason = "Persistent failure after 5 attempts";
        
        // Mock retry history
        List<DeliveryAttemptEntity> mockAttempts = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            mockAttempts.add(DeliveryAttemptEntity.builder()
                .messageId("test-message-123")
                .attemptNumber(i)
                .deliveryResult("HTTP_ERROR")
                .errorMessage("HTTP 500")
                .attemptTime(LocalDateTime.now())
                .build());
        }
        when(deliveryAttemptMapper.selectList(any(LambdaUpdateWrapper.class)))
            .thenReturn(mockAttempts);
        
        // Act
        retryScheduler.scheduleRetry(testMessage, 5, failureReason);
        
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
        
        assertEquals("test-message-123", messageCaptor.getValue().getMessageId());
        assertEquals(failureReason, reasonCaptor.getValue());
        assertEquals(5, historyCaptor.getValue().size());
        
        // Assert - verify message status updated to DEAD_LETTER
        ArgumentCaptor<LambdaUpdateWrapper> wrapperCaptor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(messageMapper, times(1)).update(eq(null), wrapperCaptor.capture());
        
        // Verify message NOT republished to main queue (moved to DLQ instead)
        verify(rabbitMQPublisher, never()).publishToMainQueue(any(Message.class));
    }
    
    @Test
    void testScheduleRetry_Attempt6_MovesToDLQ() {
        // Arrange - attempt > 5 should also move to DLQ
        String failureReason = "Exceeded max retries";
        
        when(deliveryAttemptMapper.selectList(any(LambdaUpdateWrapper.class)))
            .thenReturn(new ArrayList<>());
        
        // Act
        retryScheduler.scheduleRetry(testMessage, 6, failureReason);
        
        // Assert - verify message moved to DLQ
        verify(rabbitMQPublisher).publishToDeadLetterQueue(any(), eq(failureReason), any());
        
        // Verify message NOT republished to main queue
        verify(rabbitMQPublisher, never()).publishToMainQueue(any(Message.class));
    }
    
    @Test
    void testScheduleRetry_NullMessage_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            retryScheduler.scheduleRetry(null, 1, "failure");
        });
    }
    
    @Test
    void testScheduleRetry_InvalidAttemptNumber_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            retryScheduler.scheduleRetry(testMessage, 0, "failure");
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            retryScheduler.scheduleRetry(testMessage, -1, "failure");
        });
    }
    
    @Test
    void testScheduleRetry_NullFailureReason_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            retryScheduler.scheduleRetry(testMessage, 1, null);
        });
    }
    
    @Test
    void testScheduleRetry_EmptyFailureReason_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            retryScheduler.scheduleRetry(testMessage, 1, "");
        });
    }
    
    @Test
    void testScheduleRetry_DatabaseFailure_DoesNotThrowException() {
        // Arrange - simulate database failure
        doThrow(new RuntimeException("Database error"))
            .when(deliveryAttemptMapper).insert(any(DeliveryAttemptEntity.class));
        
        // Act & Assert - should not throw exception (error logged)
        assertDoesNotThrow(() -> {
            retryScheduler.scheduleRetry(testMessage, 1, "failure");
        });
    }
    
    @Test
    void testScheduleRetry_PublishFailure_DoesNotThrowException() throws InterruptedException {
        // Arrange - simulate publish failure
        doThrow(new RuntimeException("Publish error"))
            .when(rabbitMQPublisher).publishToMainQueue(any(Message.class));
        
        // Act & Assert - should not throw exception (error logged)
        assertDoesNotThrow(() -> {
            retryScheduler.scheduleRetry(testMessage, 1, "failure");
        });
        
        // Wait for scheduled task
        TimeUnit.SECONDS.sleep(2);
    }
}
