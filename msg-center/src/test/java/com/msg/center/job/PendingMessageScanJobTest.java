package com.msg.center.job;

import com.msg.center.statemachine.MessageStateMachine;
import com.msg.common.entity.MessageEntity;
import com.msg.common.enums.MessageStatus;
import com.msg.common.mapper.MessageMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PendingMessageScanJob 单元测试
 * 覆盖: 无到期消息、正常批次投递、MQ投递失败、状态更新失败
 */
@ExtendWith(MockitoExtension.class)
class PendingMessageScanJobTest {

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @Mock
    private MessageStateMachine stateMachine;

    private PendingMessageScanJob scanJob;

    @BeforeEach
    void setUp() {
        scanJob = new PendingMessageScanJob(messageMapper, rocketMQTemplate, stateMachine);
        scanJob.setBatchSize(100);
    }

    @Test
    void scanAndDeliver_noPendingMessages_doesNothing() {
        when(messageMapper.selectPendingExpiredMessages(any(LocalDateTime.class), eq(100)))
                .thenReturn(Collections.emptyList());

        scanJob.scanAndDeliverExpiredMessages();

        verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class));
        verify(stateMachine, never()).transitStatus(anyString(), any(), any());
    }

    @Test
    void scanAndDeliver_withExpiredMessages_sendsToMqAndUpdatesStatus() {
        MessageEntity msg1 = MessageEntity.builder()
                .msgId("msg-001").status(MessageStatus.PENDING.name())
                .sendTime(LocalDateTime.now().minusMinutes(5)).build();
        MessageEntity msg2 = MessageEntity.builder()
                .msgId("msg-002").status(MessageStatus.PENDING.name())
                .sendTime(LocalDateTime.now().minusMinutes(1)).build();

        when(messageMapper.selectPendingExpiredMessages(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(msg1, msg2));
        when(rocketMQTemplate.syncSend(eq(PendingMessageScanJob.TOPIC_MSG_SEND), any(Message.class)))
                .thenReturn(null);
        when(stateMachine.transitStatus(anyString(), eq(MessageStatus.PENDING), eq(MessageStatus.SENDING)))
                .thenReturn(true);

        scanJob.scanAndDeliverExpiredMessages();

        verify(rocketMQTemplate, times(2)).syncSend(eq(PendingMessageScanJob.TOPIC_MSG_SEND), any(Message.class));
        verify(stateMachine).transitStatus("msg-001", MessageStatus.PENDING, MessageStatus.SENDING);
        verify(stateMachine).transitStatus("msg-002", MessageStatus.PENDING, MessageStatus.SENDING);
    }

    @Test
    void scanAndDeliver_mqSendFails_continuesWithNextMessage() {
        MessageEntity msg1 = MessageEntity.builder()
                .msgId("msg-fail").status(MessageStatus.PENDING.name())
                .sendTime(LocalDateTime.now().minusMinutes(5)).build();
        MessageEntity msg2 = MessageEntity.builder()
                .msgId("msg-ok").status(MessageStatus.PENDING.name())
                .sendTime(LocalDateTime.now().minusMinutes(1)).build();

        when(messageMapper.selectPendingExpiredMessages(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(msg1, msg2));
        // First message MQ send fails
        when(rocketMQTemplate.syncSend(eq(PendingMessageScanJob.TOPIC_MSG_SEND), any(Message.class)))
                .thenThrow(new RuntimeException("Broker unavailable"))
                .thenReturn(null);
        when(stateMachine.transitStatus("msg-ok", MessageStatus.PENDING, MessageStatus.SENDING))
                .thenReturn(true);

        scanJob.scanAndDeliverExpiredMessages();

        // Should still attempt second message
        verify(rocketMQTemplate, times(2)).syncSend(eq(PendingMessageScanJob.TOPIC_MSG_SEND), any(Message.class));
        // First message should not have status updated (MQ failed)
        verify(stateMachine, never()).transitStatus(eq("msg-fail"), any(), any());
        // Second message should succeed
        verify(stateMachine).transitStatus("msg-ok", MessageStatus.PENDING, MessageStatus.SENDING);
    }

    @Test
    void scanAndDeliver_statusTransitionFails_continuesProcessing() {
        MessageEntity msg1 = MessageEntity.builder()
                .msgId("msg-stale").status(MessageStatus.PENDING.name())
                .sendTime(LocalDateTime.now().minusMinutes(5)).build();

        when(messageMapper.selectPendingExpiredMessages(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(msg1));
        when(rocketMQTemplate.syncSend(eq(PendingMessageScanJob.TOPIC_MSG_SEND), any(Message.class)))
                .thenReturn(null);
        // Status transition fails (message already processed by another instance)
        when(stateMachine.transitStatus("msg-stale", MessageStatus.PENDING, MessageStatus.SENDING))
                .thenReturn(false);

        scanJob.scanAndDeliverExpiredMessages();

        verify(rocketMQTemplate).syncSend(eq(PendingMessageScanJob.TOPIC_MSG_SEND), any(Message.class));
        verify(stateMachine).transitStatus("msg-stale", MessageStatus.PENDING, MessageStatus.SENDING);
    }

    @Test
    void scanAndDeliver_singleMessage_processesCorrectly() {
        MessageEntity msg = MessageEntity.builder()
                .msgId("msg-single").status(MessageStatus.PENDING.name())
                .sendTime(LocalDateTime.now().minusSeconds(30)).build();

        when(messageMapper.selectPendingExpiredMessages(any(LocalDateTime.class), eq(100)))
                .thenReturn(List.of(msg));
        when(rocketMQTemplate.syncSend(eq(PendingMessageScanJob.TOPIC_MSG_SEND), any(Message.class)))
                .thenReturn(null);
        when(stateMachine.transitStatus("msg-single", MessageStatus.PENDING, MessageStatus.SENDING))
                .thenReturn(true);

        scanJob.scanAndDeliverExpiredMessages();

        verify(rocketMQTemplate).syncSend(eq(PendingMessageScanJob.TOPIC_MSG_SEND), any(Message.class));
        verify(stateMachine).transitStatus("msg-single", MessageStatus.PENDING, MessageStatus.SENDING);
    }
}
