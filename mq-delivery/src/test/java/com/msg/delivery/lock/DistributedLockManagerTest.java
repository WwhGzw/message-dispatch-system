package com.msg.delivery.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DistributedLockManager.
 * 
 * Tests cover:
 * - Successful lock acquisition
 * - Failed lock acquisition (already locked)
 * - Lock release by owner
 * - Lock release attempt by non-owner
 * - Null messageId handling
 * - Redis connection errors
 */
@ExtendWith(MockitoExtension.class)
class DistributedLockManagerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private DistributedLockManager lockManager;

    @BeforeEach
    void setUp() {
        lockManager = new DistributedLockManager(redisTemplate);
    }

    @Test
    void testTryAcquire_Success() {
        // Given
        String messageId = "test-message-123";
        int timeoutSeconds = 60;
        
        when(redisTemplate.execute(
            any(RedisScript.class),
            anyList(),
            anyString(),
            anyString()
        )).thenReturn(1L);

        // When
        boolean result = lockManager.tryAcquire(messageId, timeoutSeconds);

        // Then
        assertTrue(result, "Lock should be acquired successfully");
        
        // Verify Redis script execution
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> arg1Captor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> arg2Captor = ArgumentCaptor.forClass(String.class);
        
        verify(redisTemplate).execute(
            any(RedisScript.class),
            keysCaptor.capture(),
            arg1Captor.capture(),
            arg2Captor.capture()
        );
        
        // Verify lock key format
        List<String> keys = keysCaptor.getValue();
        assertEquals(1, keys.size());
        assertEquals("lock:message:test-message-123", keys.get(0));
        
        // Verify timeout
        assertEquals("60", arg2Captor.getValue());
    }

    @Test
    void testTryAcquire_AlreadyLocked() {
        // Given
        String messageId = "test-message-123";
        int timeoutSeconds = 60;
        
        when(redisTemplate.execute(
            any(RedisScript.class),
            anyList(),
            anyString(),
            anyString()
        )).thenReturn(0L);

        // When
        boolean result = lockManager.tryAcquire(messageId, timeoutSeconds);

        // Then
        assertFalse(result, "Lock acquisition should fail when already locked");
    }

    @Test
    void testTryAcquire_NullMessageId() {
        // When
        boolean result = lockManager.tryAcquire(null, 60);

        // Then
        assertFalse(result, "Lock acquisition should fail for null messageId");
        verify(redisTemplate, never()).execute(any(), anyList(), anyString(), anyString());
    }

    @Test
    void testTryAcquire_RedisException() {
        // Given
        String messageId = "test-message-123";
        
        when(redisTemplate.execute(
            any(RedisScript.class),
            anyList(),
            anyString(),
            anyString()
        )).thenThrow(new RuntimeException("Redis connection error"));

        // When
        boolean result = lockManager.tryAcquire(messageId, 60);

        // Then
        assertFalse(result, "Lock acquisition should fail on Redis exception");
    }

    @Test
    void testRelease_Success() {
        // Given
        String messageId = "test-message-123";
        
        when(redisTemplate.execute(
            any(RedisScript.class),
            anyList(),
            anyString()
        )).thenReturn(1L);

        // When
        lockManager.release(messageId);

        // Then
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        
        verify(redisTemplate).execute(
            any(RedisScript.class),
            keysCaptor.capture(),
            anyString()
        );
        
        // Verify lock key format
        List<String> keys = keysCaptor.getValue();
        assertEquals(1, keys.size());
        assertEquals("lock:message:test-message-123", keys.get(0));
    }

    @Test
    void testRelease_NotOwned() {
        // Given
        String messageId = "test-message-123";
        
        when(redisTemplate.execute(
            any(RedisScript.class),
            anyList(),
            anyString()
        )).thenReturn(0L);

        // When
        lockManager.release(messageId);

        // Then - should not throw exception, just log warning
        verify(redisTemplate).execute(
            any(RedisScript.class),
            anyList(),
            anyString()
        );
    }

    @Test
    void testRelease_NullMessageId() {
        // When
        lockManager.release(null);

        // Then
        verify(redisTemplate, never()).execute(any(), anyList(), anyString());
    }

    @Test
    void testRelease_RedisException() {
        // Given
        String messageId = "test-message-123";
        
        when(redisTemplate.execute(
            any(RedisScript.class),
            anyList(),
            anyString()
        )).thenThrow(new RuntimeException("Redis connection error"));

        // When - should not throw exception
        assertDoesNotThrow(() -> lockManager.release(messageId));
    }

    @Test
    void testLockKeyFormat() {
        // Given
        String messageId = "msg-456";
        
        when(redisTemplate.execute(
            any(RedisScript.class),
            anyList(),
            anyString(),
            anyString()
        )).thenReturn(1L);

        // When
        lockManager.tryAcquire(messageId, 60);

        // Then
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(
            any(RedisScript.class),
            keysCaptor.capture(),
            anyString(),
            anyString()
        );
        
        assertEquals("lock:message:msg-456", keysCaptor.getValue().get(0));
    }

    @Test
    void testMultipleAcquireAttempts() {
        // Given
        String messageId = "test-message-123";
        
        // First attempt succeeds
        when(redisTemplate.execute(
            any(RedisScript.class),
            anyList(),
            anyString(),
            anyString()
        )).thenReturn(1L).thenReturn(0L);

        // When
        boolean firstAttempt = lockManager.tryAcquire(messageId, 60);
        boolean secondAttempt = lockManager.tryAcquire(messageId, 60);

        // Then
        assertTrue(firstAttempt, "First lock acquisition should succeed");
        assertFalse(secondAttempt, "Second lock acquisition should fail");
    }

    @Test
    void testAcquireWithDifferentTimeouts() {
        // Given
        String messageId = "test-message-123";
        
        when(redisTemplate.execute(
            any(RedisScript.class),
            anyList(),
            anyString(),
            anyString()
        )).thenReturn(1L);

        // When
        lockManager.tryAcquire(messageId, 30);

        // Then
        ArgumentCaptor<String> timeoutCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).execute(
            any(RedisScript.class),
            anyList(),
            anyString(),
            timeoutCaptor.capture()
        );
        
        assertEquals("30", timeoutCaptor.getValue());
    }
}
