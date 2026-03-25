package com.msg.center.service;

import com.msg.common.dto.IdempotentResult;
import com.msg.common.entity.MessageEntity;
import com.msg.common.mapper.MessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IdempotentService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class IdempotentServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private IdempotentService idempotentService;

    private static final String BIZ_TYPE = "ORDER_NOTIFY";
    private static final String BIZ_ID = "ORDER_001";
    private static final String CHANNEL = "SMS";
    private static final String EXPECTED_KEY = "idem:ORDER_NOTIFY:ORDER_001:SMS";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ========== checkAndLock 测试 ==========

    @Test
    void checkAndLock_redisLockAcquiredAndDbEmpty_returnsPass() {
        when(valueOperations.setIfAbsent(eq(EXPECTED_KEY), eq("1"), any(Duration.class)))
                .thenReturn(true);
        when(messageMapper.selectByBizKey(BIZ_TYPE, BIZ_ID, CHANNEL)).thenReturn(null);

        IdempotentResult result = idempotentService.checkAndLock(BIZ_TYPE, BIZ_ID, CHANNEL);

        assertFalse(result.isDuplicate());
        assertNull(result.getExistingMsgId());
    }

    @Test
    void checkAndLock_redisLockAcquiredButDbHasRecord_returnsDuplicate() {
        when(valueOperations.setIfAbsent(eq(EXPECTED_KEY), eq("1"), any(Duration.class)))
                .thenReturn(true);
        MessageEntity existing = MessageEntity.builder().msgId("msg-existing-001").build();
        when(messageMapper.selectByBizKey(BIZ_TYPE, BIZ_ID, CHANNEL)).thenReturn(existing);

        IdempotentResult result = idempotentService.checkAndLock(BIZ_TYPE, BIZ_ID, CHANNEL);

        assertTrue(result.isDuplicate());
        assertEquals("msg-existing-001", result.getExistingMsgId());
    }

    @Test
    void checkAndLock_redisKeyAlreadyExists_returnsDuplicate() {
        when(valueOperations.setIfAbsent(eq(EXPECTED_KEY), eq("1"), any(Duration.class)))
                .thenReturn(false);
        MessageEntity existing = MessageEntity.builder().msgId("msg-existing-002").build();
        when(messageMapper.selectByBizKey(BIZ_TYPE, BIZ_ID, CHANNEL)).thenReturn(existing);

        IdempotentResult result = idempotentService.checkAndLock(BIZ_TYPE, BIZ_ID, CHANNEL);

        assertTrue(result.isDuplicate());
        assertEquals("msg-existing-002", result.getExistingMsgId());
    }

    @Test
    void checkAndLock_redisKeyExistsButDbEmpty_returnsPass() {
        when(valueOperations.setIfAbsent(eq(EXPECTED_KEY), eq("1"), any(Duration.class)))
                .thenReturn(false);
        when(messageMapper.selectByBizKey(BIZ_TYPE, BIZ_ID, CHANNEL)).thenReturn(null);

        IdempotentResult result = idempotentService.checkAndLock(BIZ_TYPE, BIZ_ID, CHANNEL);

        assertFalse(result.isDuplicate());
    }

    @Test
    void checkAndLock_redisUnavailable_fallsBackToDb_noDuplicate() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));
        when(messageMapper.selectByBizKey(BIZ_TYPE, BIZ_ID, CHANNEL)).thenReturn(null);

        IdempotentResult result = idempotentService.checkAndLock(BIZ_TYPE, BIZ_ID, CHANNEL);

        assertFalse(result.isDuplicate());
    }

    @Test
    void checkAndLock_redisUnavailable_fallsBackToDb_duplicate() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));
        MessageEntity existing = MessageEntity.builder().msgId("msg-existing-003").build();
        when(messageMapper.selectByBizKey(BIZ_TYPE, BIZ_ID, CHANNEL)).thenReturn(existing);

        IdempotentResult result = idempotentService.checkAndLock(BIZ_TYPE, BIZ_ID, CHANNEL);

        assertTrue(result.isDuplicate());
        assertEquals("msg-existing-003", result.getExistingMsgId());
    }

    // ========== releaseLock 测试 ==========

    @Test
    void releaseLock_success() {
        when(redisTemplate.delete(EXPECTED_KEY)).thenReturn(true);

        idempotentService.releaseLock(BIZ_TYPE, BIZ_ID, CHANNEL);

        verify(redisTemplate).delete(EXPECTED_KEY);
    }

    @Test
    void releaseLock_redisUnavailable_doesNotThrow() {
        when(redisTemplate.delete(EXPECTED_KEY)).thenThrow(new RuntimeException("Redis down"));

        assertDoesNotThrow(() -> idempotentService.releaseLock(BIZ_TYPE, BIZ_ID, CHANNEL));
    }

    // ========== handleDuplicateKey 测试 ==========

    @Test
    void handleDuplicateKey_existingRecord_returnsDuplicate() {
        MessageEntity existing = MessageEntity.builder().msgId("msg-dup-001").build();
        when(messageMapper.selectByBizKey(BIZ_TYPE, BIZ_ID, CHANNEL)).thenReturn(existing);

        IdempotentResult result = idempotentService.handleDuplicateKey(BIZ_TYPE, BIZ_ID, CHANNEL);

        assertTrue(result.isDuplicate());
        assertEquals("msg-dup-001", result.getExistingMsgId());
    }

    @Test
    void handleDuplicateKey_noRecord_returnsPass() {
        when(messageMapper.selectByBizKey(BIZ_TYPE, BIZ_ID, CHANNEL)).thenReturn(null);

        IdempotentResult result = idempotentService.handleDuplicateKey(BIZ_TYPE, BIZ_ID, CHANNEL);

        assertFalse(result.isDuplicate());
    }
}
