package com.msg.center.service;

import com.msg.center.statemachine.MessageStateMachine;
import com.msg.common.enums.MessageStatus;
import com.msg.common.mapper.MessageMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RetryHandler 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RetryHandlerTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Mock
    private MessageStateMachine stateMachine;

    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private RetryHandler retryHandler;

    // ========== calculateDelay 各重试级别返回正确延迟 ==========

    @Test
    void calculateDelay_retry1_returns10() {
        assertEquals(10L, retryHandler.calculateDelay(1));
    }

    @Test
    void calculateDelay_retry2_returns30() {
        assertEquals(30L, retryHandler.calculateDelay(2));
    }

    @Test
    void calculateDelay_retry3_returns60() {
        assertEquals(60L, retryHandler.calculateDelay(3));
    }

    @Test
    void calculateDelay_retry4_returns300() {
        assertEquals(300L, retryHandler.calculateDelay(4));
    }

    @Test
    void calculateDelay_retry5_returns1800() {
        assertEquals(1800L, retryHandler.calculateDelay(5));
    }

    @Test
    void calculateDelay_retry6_returns3600() {
        assertEquals(3600L, retryHandler.calculateDelay(6));
    }

    @Test
    void calculateDelay_retry7_returns7200() {
        assertEquals(7200L, retryHandler.calculateDelay(7));
    }

    @Test
    void calculateDelay_retry8_returns21600() {
        assertEquals(21600L, retryHandler.calculateDelay(8));
    }

    @Test
    void calculateDelay_retry9_returns43200() {
        assertEquals(43200L, retryHandler.calculateDelay(9));
    }

    // ========== calculateDelay 超出范围返回 -1 ==========

    @Test
    void calculateDelay_retry0_returnsNegativeOne() {
        assertEquals(-1L, retryHandler.calculateDelay(0));
    }

    @Test
    void calculateDelay_retry10_returnsNegativeOne() {
        assertEquals(-1L, retryHandler.calculateDelay(10));
    }

    @Test
    void calculateDelay_retryNegative_returnsNegativeOne() {
        assertEquals(-1L, retryHandler.calculateDelay(-1));
    }

    // ========== calculateDelay 延迟严格单调递增 ==========

    @Test
    void calculateDelay_delaysAreStrictlyMonotonicallyIncreasing() {
        for (int i = 1; i < 9; i++) {
            long current = retryHandler.calculateDelay(i);
            long next = retryHandler.calculateDelay(i + 1);
            assertTrue(current < next,
                    "delay(" + i + ")=" + current + " should be < delay(" + (i + 1) + ")=" + next);
        }
    }

    // ========== submitRetry 正常重试投递 ==========

    @Test
    void submitRetry_validRetryTimes_sendsToRetryTopicAndReturnsTrue() {
        boolean result = retryHandler.submitRetry("msg-001", 1);

        assertTrue(result);
        verify(rocketMQTemplate).syncSend(
                eq(RetryHandler.TOPIC_MSG_RETRY),
                any(Message.class),
                eq(3000L),
                anyInt());
    }

    @Test
    void submitRetry_sendsWithCorrectDelayLevel() {
        // retryTimes=1 → delay=10s → RocketMQ level 3 (10s)
        retryHandler.submitRetry("msg-002", 1);

        verify(rocketMQTemplate).syncSend(
                eq(RetryHandler.TOPIC_MSG_RETRY),
                any(Message.class),
                eq(3000L),
                eq(3)); // 10s maps to delay level 3
    }

    @Test
    void submitRetry_retryTimes4_sendsWithCorrectDelayLevel() {
        // retryTimes=4 → delay=300s (5min) → RocketMQ level 9 (5m)
        retryHandler.submitRetry("msg-003", 4);

        verify(rocketMQTemplate).syncSend(
                eq(RetryHandler.TOPIC_MSG_RETRY),
                any(Message.class),
                eq(3000L),
                eq(9)); // 300s maps to delay level 9
    }

    // ========== submitRetry 超过最大重试次数转死信 ==========

    @Test
    void submitRetry_exceedsMaxRetry_delegatesToDeadLetterAndReturnsFalse() {
        when(stateMachine.transitStatus("msg-004", MessageStatus.FAILED, MessageStatus.DEAD_LETTER))
                .thenReturn(true);

        boolean result = retryHandler.submitRetry("msg-004", 10);

        assertFalse(result);
        // Should send to dead letter topic, not retry topic
        verify(rocketMQTemplate).syncSend(
                eq(RetryHandler.TOPIC_MSG_DEAD_LETTER),
                any(Message.class));
        verify(rocketMQTemplate, never()).syncSend(
                eq(RetryHandler.TOPIC_MSG_RETRY),
                any(Message.class),
                anyLong(),
                anyInt());
        verify(stateMachine).transitStatus("msg-004", MessageStatus.FAILED, MessageStatus.DEAD_LETTER);
    }

    // ========== submitRetry MQ投递失败返回false ==========

    @Test
    void submitRetry_mqSendFails_returnsFalse() {
        doThrow(new RuntimeException("MQ unavailable"))
                .when(rocketMQTemplate).syncSend(
                        eq(RetryHandler.TOPIC_MSG_RETRY),
                        any(Message.class),
                        anyLong(),
                        anyInt());

        boolean result = retryHandler.submitRetry("msg-005", 1);

        assertFalse(result);
    }

    // ========== submitDeadLetter 正常投递 ==========

    @Test
    void submitDeadLetter_sendsToDeadLetterTopicAndTransitsStatus() {
        when(stateMachine.transitStatus("msg-006", MessageStatus.FAILED, MessageStatus.DEAD_LETTER))
                .thenReturn(true);

        retryHandler.submitDeadLetter("msg-006", "渠道发送器不存在");

        verify(rocketMQTemplate).syncSend(
                eq(RetryHandler.TOPIC_MSG_DEAD_LETTER),
                any(Message.class));
        verify(stateMachine).transitStatus("msg-006", MessageStatus.FAILED, MessageStatus.DEAD_LETTER);
    }

    @Test
    void submitDeadLetter_statusTransitionFails_doesNotThrow() {
        when(stateMachine.transitStatus("msg-007", MessageStatus.FAILED, MessageStatus.DEAD_LETTER))
                .thenReturn(false);

        // Should not throw even if status transition fails
        assertDoesNotThrow(() -> retryHandler.submitDeadLetter("msg-007", "some reason"));

        verify(stateMachine).transitStatus("msg-007", MessageStatus.FAILED, MessageStatus.DEAD_LETTER);
    }

    @Test
    void submitDeadLetter_mqSendFails_stillAttemptsStatusTransition() {
        doThrow(new RuntimeException("MQ down"))
                .when(rocketMQTemplate).syncSend(
                        eq(RetryHandler.TOPIC_MSG_DEAD_LETTER),
                        any(Message.class));
        when(stateMachine.transitStatus("msg-008", MessageStatus.FAILED, MessageStatus.DEAD_LETTER))
                .thenReturn(true);

        assertDoesNotThrow(() -> retryHandler.submitDeadLetter("msg-008", "failure reason"));

        // Status transition should still be attempted even if MQ send fails
        verify(stateMachine).transitStatus("msg-008", MessageStatus.FAILED, MessageStatus.DEAD_LETTER);
    }

    // ========== mapSecondsToDelayLevel ==========

    @Test
    void mapSecondsToDelayLevel_10s_returnsLevel3() {
        assertEquals(3, retryHandler.mapSecondsToDelayLevel(10));
    }

    @Test
    void mapSecondsToDelayLevel_30s_returnsLevel4() {
        assertEquals(4, retryHandler.mapSecondsToDelayLevel(30));
    }

    @Test
    void mapSecondsToDelayLevel_60s_returnsLevel5() {
        assertEquals(5, retryHandler.mapSecondsToDelayLevel(60));
    }

    @Test
    void mapSecondsToDelayLevel_300s_returnsLevel9() {
        assertEquals(9, retryHandler.mapSecondsToDelayLevel(300));
    }

    @Test
    void mapSecondsToDelayLevel_1800s_returnsLevel16() {
        assertEquals(16, retryHandler.mapSecondsToDelayLevel(1800));
    }

    @Test
    void mapSecondsToDelayLevel_3600s_returnsLevel17() {
        assertEquals(17, retryHandler.mapSecondsToDelayLevel(3600));
    }

    @Test
    void mapSecondsToDelayLevel_7200s_returnsLevel18() {
        assertEquals(18, retryHandler.mapSecondsToDelayLevel(7200));
    }

    @Test
    void mapSecondsToDelayLevel_exceedsMax_returnsMaxLevel() {
        // 21600s (6h) exceeds max delay level (2h=7200s), returns max level 18
        assertEquals(18, retryHandler.mapSecondsToDelayLevel(21600));
    }

    @Test
    void mapSecondsToDelayLevel_1s_returnsLevel1() {
        assertEquals(1, retryHandler.mapSecondsToDelayLevel(1));
    }

    @Test
    void mapSecondsToDelayLevel_2s_returnsLevel2() {
        // 2s → next level >= 2s is 5s → level 2
        assertEquals(2, retryHandler.mapSecondsToDelayLevel(2));
    }
}
