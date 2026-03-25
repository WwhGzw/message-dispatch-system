package com.msg.center.service;

import com.msg.access.auth.HmacSignatureUtil;
import com.msg.access.dto.ReceiptCallback;
import com.msg.center.statemachine.MessageStateMachine;
import com.msg.common.entity.MessageEntity;
import com.msg.common.entity.MessageReceiptEntity;
import com.msg.common.enums.MessageStatus;
import com.msg.common.mapper.MessageMapper;
import com.msg.common.mapper.MessageReceiptMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 渠道回执回调处理服务
 * 验证回执签名 → 插入回执记录 → 更新消息最终状态
 */
@Service
public class ReceiptService {

    private static final Logger log = LoggerFactory.getLogger(ReceiptService.class);

    @Value("${msg.receipt.secret:default-receipt-secret}")
    private String receiptSecret;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private MessageReceiptMapper messageReceiptMapper;

    @Autowired
    private MessageStateMachine stateMachine;

    /**
     * 处理渠道回执回调
     *
     * @param callback 回执回调数据
     * @throws IllegalArgumentException 签名验证失败时抛出
     */
    public void processReceipt(ReceiptCallback callback) {
        // Step 1: 验证回执签名
        String signContent = buildSignContent(callback);
        boolean valid = HmacSignatureUtil.verifySignature(receiptSecret, signContent, callback.getSignature());
        if (!valid) {
            log.warn("回执签名验证失败: msgId={}, signature={}", callback.getMsgId(), callback.getSignature());
            throw new IllegalArgumentException("回执签名验证失败");
        }

        // Step 2: 查询消息
        MessageEntity message = messageMapper.selectByMsgId(callback.getMsgId());
        if (message == null) {
            log.warn("回执对应消息不存在: msgId={}", callback.getMsgId());
            throw new IllegalArgumentException("消息不存在: " + callback.getMsgId());
        }

        // Step 3: 插入回执记录
        MessageReceiptEntity receipt = MessageReceiptEntity.builder()
                .msgId(callback.getMsgId())
                .channel(callback.getChannel())
                .channelMsgId(callback.getChannelMsgId())
                .receiptStatus(callback.getReceiptStatus())
                .receiptTime(LocalDateTime.now())
                .rawData(callback.getRawData())
                .createTime(LocalDateTime.now())
                .build();
        messageReceiptMapper.insert(receipt);

        // Step 4: 根据回执状态更新消息最终状态
        updateMessageStatus(message, callback.getReceiptStatus());
    }

    /**
     * 根据回执状态映射并更新消息状态
     * DELIVERED → SUCCESS, REJECTED → FAILED
     */
    private void updateMessageStatus(MessageEntity message, String receiptStatus) {
        MessageStatus currentStatus = MessageStatus.valueOf(message.getStatus());

        // 终态消息不再更新
        if (currentStatus.isTerminal()) {
            log.info("消息已处于终态，跳过状态更新: msgId={}, status={}", message.getMsgId(), currentStatus);
            return;
        }

        MessageStatus targetStatus;
        switch (receiptStatus) {
            case "DELIVERED":
            case "READ":
                targetStatus = MessageStatus.SUCCESS;
                break;
            case "REJECTED":
                targetStatus = MessageStatus.FAILED;
                break;
            default:
                log.info("未知回执状态，不更新消息状态: receiptStatus={}", receiptStatus);
                return;
        }

        boolean transitioned = stateMachine.transitStatus(message.getMsgId(), currentStatus, targetStatus);
        if (transitioned) {
            log.info("回执处理完成，消息状态更新: msgId={}, {} → {}", message.getMsgId(), currentStatus, targetStatus);
        } else {
            log.warn("回执处理状态更新失败: msgId={}, {} → {}", message.getMsgId(), currentStatus, targetStatus);
        }
    }

    /**
     * 构建签名内容：msgId + channel + channelMsgId + receiptStatus
     */
    String buildSignContent(ReceiptCallback callback) {
        return callback.getMsgId() + callback.getChannel() + callback.getChannelMsgId() + callback.getReceiptStatus();
    }
}
