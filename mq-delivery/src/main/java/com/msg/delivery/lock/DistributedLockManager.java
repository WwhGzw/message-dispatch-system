package com.msg.delivery.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;

/**
 * Distributed lock manager using Redis for coordinating message processing across multiple instances.
 * 
 * Uses Redis SET NX EX command with Lua scripts to ensure atomic lock operations.
 * Lock key format: "lock:message:{messageId}"
 * Lock TTL: 60 seconds (prevents deadlock if holder crashes)
 */
@Slf4j
@Component
public class DistributedLockManager {

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;
    
    // Lua script for atomic lock acquisition
    private static final String ACQUIRE_SCRIPT = 
        "if redis.call('exists', KEYS[1]) == 0 then " +
        "  redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2]) " +
        "  return 1 " +
        "else " +
        "  return 0 " +
        "end";
    
    // Lua script for atomic lock release with ownership verification
    private static final String RELEASE_SCRIPT = 
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "  return redis.call('del', KEYS[1]) " +
        "else " +
        "  return 0 " +
        "end";
    
    public DistributedLockManager(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.instanceId = UUID.randomUUID().toString();
    }

    /**
     * Try to acquire distributed lock for a message.
     * 
     * @param messageId Message identifier as lock key
     * @param timeoutSeconds Lock timeout in seconds (TTL to prevent deadlock)
     * @return true if lock acquired, false otherwise
     * 
     * Implementation:
     *   - Uses Redis SET NX EX command via Lua script for atomicity
     *   - Key format: "lock:message:{messageId}"
     *   - Value: {instanceId}:{threadId} for ownership tracking
     *   - TTL: timeoutSeconds (prevents deadlock if holder crashes)
     * 
     * Preconditions:
     *   - messageId is not null
     *   - Redis connection is available
     * 
     * Postconditions:
     *   - If successful, lock exists in Redis with TTL
     *   - If failed, no lock created
     */
    public boolean tryAcquire(String messageId, int timeoutSeconds) {
        if (messageId == null) {
            log.warn("Cannot acquire lock: messageId is null");
            return false;
        }
        
        String lockKey = buildLockKey(messageId);
        String lockValue = buildLockValue();
        
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(ACQUIRE_SCRIPT, Long.class);
            Long result = redisTemplate.execute(
                script,
                Collections.singletonList(lockKey),
                lockValue,
                String.valueOf(timeoutSeconds)
            );
            
            boolean acquired = result != null && result == 1;
            
            if (acquired) {
                log.debug("Lock acquired for message: {}, lockKey: {}", messageId, lockKey);
            } else {
                log.debug("Failed to acquire lock for message: {}, lockKey: {} (already locked)", 
                    messageId, lockKey);
            }
            
            return acquired;
        } catch (Exception e) {
            log.error("Error acquiring lock for message: {}, lockKey: {}", messageId, lockKey, e);
            return false;
        }
    }

    /**
     * Release distributed lock for a message.
     * 
     * @param messageId Message identifier as lock key
     * 
     * Implementation:
     *   - Verifies lock value matches current thread/instance (ownership check)
     *   - Deletes lock key from Redis using Lua script (atomic operation)
     *   - Only the lock owner can release the lock
     * 
     * Preconditions:
     *   - messageId is not null
     *   - Redis connection is available
     * 
     * Postconditions:
     *   - If owned, lock removed from Redis
     *   - If not owned, no state change
     */
    public void release(String messageId) {
        if (messageId == null) {
            log.warn("Cannot release lock: messageId is null");
            return;
        }
        
        String lockKey = buildLockKey(messageId);
        String lockValue = buildLockValue();
        
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);
            Long result = redisTemplate.execute(
                script,
                Collections.singletonList(lockKey),
                lockValue
            );
            
            if (result != null && result == 1) {
                log.debug("Lock released for message: {}, lockKey: {}", messageId, lockKey);
            } else {
                log.warn("Failed to release lock for message: {}, lockKey: {} (not owned by this instance)", 
                    messageId, lockKey);
            }
        } catch (Exception e) {
            log.error("Error releasing lock for message: {}, lockKey: {}", messageId, lockKey, e);
        }
    }

    /**
     * Build lock key in the format: "lock:message:{messageId}"
     */
    private String buildLockKey(String messageId) {
        return "lock:message:" + messageId;
    }

    /**
     * Build lock value in the format: "{instanceId}:{threadId}"
     * This allows tracking which instance and thread owns the lock.
     */
    private String buildLockValue() {
        return instanceId + ":" + Thread.currentThread().getId();
    }
}
