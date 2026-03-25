package com.msg.center.statemachine;

import com.msg.common.enums.MessageStatus;
import com.msg.common.mapper.MessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

/**
 * 消息状态机
 * 管理消息生命周期中的合法状态转换，使用乐观锁保证并发安全。
 */
@Service
public class MessageStateMachine {

    /**
     * 合法状态转换集合
     */
    private static final Set<Map.Entry<MessageStatus, MessageStatus>> LEGAL_TRANSITIONS = Set.of(
            new AbstractMap.SimpleImmutableEntry<>(MessageStatus.PENDING, MessageStatus.SENDING),
            new AbstractMap.SimpleImmutableEntry<>(MessageStatus.PENDING, MessageStatus.CANCELLED),
            new AbstractMap.SimpleImmutableEntry<>(MessageStatus.SENDING, MessageStatus.SUCCESS),
            new AbstractMap.SimpleImmutableEntry<>(MessageStatus.SENDING, MessageStatus.FAILED),
            new AbstractMap.SimpleImmutableEntry<>(MessageStatus.SENDING, MessageStatus.CANCELLED),
            new AbstractMap.SimpleImmutableEntry<>(MessageStatus.FAILED, MessageStatus.RETRYING),
            new AbstractMap.SimpleImmutableEntry<>(MessageStatus.FAILED, MessageStatus.DEAD_LETTER),
            new AbstractMap.SimpleImmutableEntry<>(MessageStatus.RETRYING, MessageStatus.SENDING)
    );

    @Autowired
    private MessageMapper messageMapper;

    /**
     * 判断状态转换是否合法
     *
     * @param fromStatus 当前状态
     * @param toStatus   目标状态
     * @return true 如果转换合法
     */
    public boolean isLegalTransition(MessageStatus fromStatus, MessageStatus toStatus) {
        return LEGAL_TRANSITIONS.contains(
                new AbstractMap.SimpleImmutableEntry<>(fromStatus, toStatus));
    }

    /**
     * 执行状态流转（乐观锁）
     *
     * @param msgId      消息ID
     * @param fromStatus 当前状态
     * @param toStatus   目标状态
     * @return true 如果流转成功（合法转换且DB更新影响1行）
     */
    public boolean transitStatus(String msgId, MessageStatus fromStatus, MessageStatus toStatus) {
        if (!isLegalTransition(fromStatus, toStatus)) {
            return false;
        }
        int rows = messageMapper.updateStatus(msgId, fromStatus.name(), toStatus.name());
        return rows == 1;
    }
}
