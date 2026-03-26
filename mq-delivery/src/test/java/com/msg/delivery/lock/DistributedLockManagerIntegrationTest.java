package com.msg.delivery.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DistributedLockManager with actual Redis operations.
 * 
 * These tests verify:
 * - Lock acquisition and release with real Redis
 * - Lock TTL expiration
 * - Concurrent lock attempts from multiple threads
 * - Lock ownership verification
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.redis.host=localhost",
    "spring.redis.port=6379"
})
class DistributedLockManagerIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private DistributedLockManager lockManager;

    @BeforeEach
    void setUp() {
        lockManager = new DistributedLockManager(redisTemplate);
        // Clean up any existing test locks
        redisTemplate.delete("lock:message:test-msg-*");
    }

    @Test
    void testAcquireAndRelease() {
        // Given
        String messageId = "test-msg-001";

        // When
        boolean acquired = lockManager.tryAcquire(messageId, 60);

        // Then
        assertTrue(acquired, "Should acquire lock successfully");

        // Verify lock exists in Redis
        String lockKey = "lock:message:" + messageId;
        Boolean exists = redisTemplate.hasKey(lockKey);
        assertTrue(exists, "Lock should exist in Redis");

        // When - release lock
        lockManager.release(messageId);

        // Then - lock should be removed
        exists = redisTemplate.hasKey(lockKey);
        assertFalse(exists, "Lock should be removed from Redis");
    }

    @Test
    void testCannotAcquireSameLockTwice() {
        // Given
        String messageId = "test-msg-002";

        // When
        boolean firstAcquire = lockManager.tryAcquire(messageId, 60);
        boolean secondAcquire = lockManager.tryAcquire(messageId, 60);

        // Then
        assertTrue(firstAcquire, "First acquisition should succeed");
        assertFalse(secondAcquire, "Second acquisition should fail");

        // Cleanup
        lockManager.release(messageId);
    }

    @Test
    void testLockExpiration() throws InterruptedException {
        // Given
        String messageId = "test-msg-003";
        int shortTimeout = 2; // 2 seconds

        // When
        boolean acquired = lockManager.tryAcquire(messageId, shortTimeout);
        assertTrue(acquired, "Should acquire lock");

        // Wait for lock to expire
        Thread.sleep(3000);

        // Then - should be able to acquire again
        boolean reacquired = lockManager.tryAcquire(messageId, 60);
        assertTrue(reacquired, "Should be able to acquire expired lock");

        // Cleanup
        lockManager.release(messageId);
    }

    @Test
    void testConcurrentLockAttempts() throws InterruptedException {
        // Given
        String messageId = "test-msg-004";
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When - multiple threads try to acquire the same lock
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    boolean acquired = lockManager.tryAcquire(messageId, 60);
                    if (acquired) {
                        successCount.incrementAndGet();
                        Thread.sleep(100); // Hold lock briefly
                        lockManager.release(messageId);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // Start all threads
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);

        // Then - only one thread should have acquired the lock
        assertTrue(completed, "All threads should complete");
        assertEquals(1, successCount.get(), "Only one thread should acquire the lock");
    }

    @Test
    void testLockKeyFormat() {
        // Given
        String messageId = "msg-12345";

        // When
        lockManager.tryAcquire(messageId, 60);

        // Then
        String expectedKey = "lock:message:msg-12345";
        Boolean exists = redisTemplate.hasKey(expectedKey);
        assertTrue(exists, "Lock key should follow format: lock:message:{messageId}");

        // Cleanup
        lockManager.release(messageId);
    }

    @Test
    void testDifferentMessagesCanBeLocked() {
        // Given
        String messageId1 = "test-msg-005";
        String messageId2 = "test-msg-006";

        // When
        boolean acquired1 = lockManager.tryAcquire(messageId1, 60);
        boolean acquired2 = lockManager.tryAcquire(messageId2, 60);

        // Then
        assertTrue(acquired1, "Should acquire lock for message 1");
        assertTrue(acquired2, "Should acquire lock for message 2");

        // Cleanup
        lockManager.release(messageId1);
        lockManager.release(messageId2);
    }

    @Test
    void testReleaseWithoutOwnership() {
        // Given
        String messageId = "test-msg-007";
        
        // First lock manager acquires lock
        DistributedLockManager lockManager1 = new DistributedLockManager(redisTemplate);
        boolean acquired = lockManager1.tryAcquire(messageId, 60);
        assertTrue(acquired, "First manager should acquire lock");

        // When - second lock manager tries to release
        DistributedLockManager lockManager2 = new DistributedLockManager(redisTemplate);
        lockManager2.release(messageId);

        // Then - lock should still exist (not released by non-owner)
        String lockKey = "lock:message:" + messageId;
        Boolean exists = redisTemplate.hasKey(lockKey);
        assertTrue(exists, "Lock should still exist after non-owner release attempt");

        // Cleanup
        lockManager1.release(messageId);
    }

    @Test
    void testLockTTL() {
        // Given
        String messageId = "test-msg-008";
        int timeoutSeconds = 60;

        // When
        boolean acquired = lockManager.tryAcquire(messageId, timeoutSeconds);
        assertTrue(acquired, "Should acquire lock");

        // Then - verify TTL is set correctly
        String lockKey = "lock:message:" + messageId;
        Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
        assertNotNull(ttl, "TTL should be set");
        assertTrue(ttl > 0 && ttl <= timeoutSeconds, 
            "TTL should be positive and not exceed timeout");

        // Cleanup
        lockManager.release(messageId);
    }
}
