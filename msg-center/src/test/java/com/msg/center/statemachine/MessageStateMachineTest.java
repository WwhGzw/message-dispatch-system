package com.msg.center.statemachine;

import com.msg.common.enums.MessageStatus;
import com.msg.common.mapper.MessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * MessageStateMachine 单元测试
 */
@ExtendWith(MockitoExtension.class)
class MessageStateMachineTest {

    @Mock
    private MessageMapper messageMapper;

    @InjectMocks
    private MessageStateMachine stateMachine;

    // ========== isLegalTransition 测试 ==========

    @Test
    void isLegalTransition_pendingToSending_returnsTrue() {
        assertTrue(stateMachine.isLegalTransition(MessageStatus.PENDING, MessageStatus.SENDING));
    }

    @Test
    void isLegalTransition_pendingToCancelled_returnsTrue() {
        assertTrue(stateMachine.isLegalTransition(MessageStatus.PENDING, MessageStatus.CANCELLED));
    }

    @Test
    void isLegalTransition_sendingToSuccess_returnsTrue() {
        assertTrue(stateMachine.isLegalTransition(MessageStatus.SENDING, MessageStatus.SUCCESS));
    }

    @Test
    void isLegalTransition_sendingToFailed_returnsTrue() {
        assertTrue(stateMachine.isLegalTransition(MessageStatus.SENDING, MessageStatus.FAILED));
    }

    @Test
    void isLegalTransition_sendingToCancelled_returnsTrue() {
        assertTrue(stateMachine.isLegalTransition(MessageStatus.SENDING, MessageStatus.CANCELLED));
    }

    @Test
    void isLegalTransition_failedToRetrying_returnsTrue() {
        assertTrue(stateMachine.isLegalTransition(MessageStatus.FAILED, MessageStatus.RETRYING));
    }

    @Test
    void isLegalTransition_failedToDeadLetter_returnsTrue() {
        assertTrue(stateMachine.isLegalTransition(MessageStatus.FAILED, MessageStatus.DEAD_LETTER));
    }

    @Test
    void isLegalTransition_retryingToSending_returnsTrue() {
        assertTrue(stateMachine.isLegalTransition(MessageStatus.RETRYING, MessageStatus.SENDING));
    }

    @Test
    void isLegalTransition_illegalTransition_returnsFalse() {
        // Terminal states cannot transition
        assertFalse(stateMachine.isLegalTransition(MessageStatus.SUCCESS, MessageStatus.SENDING));
        assertFalse(stateMachine.isLegalTransition(MessageStatus.DEAD_LETTER, MessageStatus.RETRYING));
        assertFalse(stateMachine.isLegalTransition(MessageStatus.CANCELLED, MessageStatus.PENDING));

        // Invalid forward transitions
        assertFalse(stateMachine.isLegalTransition(MessageStatus.PENDING, MessageStatus.SUCCESS));
        assertFalse(stateMachine.isLegalTransition(MessageStatus.PENDING, MessageStatus.FAILED));
        assertFalse(stateMachine.isLegalTransition(MessageStatus.SENDING, MessageStatus.RETRYING));
        assertFalse(stateMachine.isLegalTransition(MessageStatus.FAILED, MessageStatus.SENDING));
        assertFalse(stateMachine.isLegalTransition(MessageStatus.RETRYING, MessageStatus.FAILED));

        // Self-transitions
        assertFalse(stateMachine.isLegalTransition(MessageStatus.PENDING, MessageStatus.PENDING));
        assertFalse(stateMachine.isLegalTransition(MessageStatus.SENDING, MessageStatus.SENDING));
    }

    // ========== transitStatus 测试 ==========

    @Test
    void transitStatus_legalTransitionAndDbUpdated_returnsTrue() {
        String msgId = "msg-001";
        when(messageMapper.updateStatus(msgId, "PENDING", "SENDING")).thenReturn(1);

        assertTrue(stateMachine.transitStatus(msgId, MessageStatus.PENDING, MessageStatus.SENDING));
        verify(messageMapper).updateStatus(msgId, "PENDING", "SENDING");
    }

    @Test
    void transitStatus_legalTransitionButOptimisticLockFails_returnsFalse() {
        String msgId = "msg-002";
        // DB returns 0 rows — optimistic lock failure (status already changed)
        when(messageMapper.updateStatus(msgId, "SENDING", "SUCCESS")).thenReturn(0);

        assertFalse(stateMachine.transitStatus(msgId, MessageStatus.SENDING, MessageStatus.SUCCESS));
        verify(messageMapper).updateStatus(msgId, "SENDING", "SUCCESS");
    }

    @Test
    void transitStatus_illegalTransition_returnsFalseWithoutDbCall() {
        String msgId = "msg-003";

        assertFalse(stateMachine.transitStatus(msgId, MessageStatus.SUCCESS, MessageStatus.SENDING));
        // Should NOT call DB for illegal transitions
        verifyNoInteractions(messageMapper);
    }

    @Test
    void transitStatus_failedToDeadLetter_returnsTrue() {
        String msgId = "msg-004";
        when(messageMapper.updateStatus(msgId, "FAILED", "DEAD_LETTER")).thenReturn(1);

        assertTrue(stateMachine.transitStatus(msgId, MessageStatus.FAILED, MessageStatus.DEAD_LETTER));
    }

    @Test
    void transitStatus_retryingToSending_returnsTrue() {
        String msgId = "msg-005";
        when(messageMapper.updateStatus(msgId, "RETRYING", "SENDING")).thenReturn(1);

        assertTrue(stateMachine.transitStatus(msgId, MessageStatus.RETRYING, MessageStatus.SENDING));
    }
}
