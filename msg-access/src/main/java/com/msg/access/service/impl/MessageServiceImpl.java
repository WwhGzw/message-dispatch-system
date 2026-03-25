package com.msg.access.service.impl;

import com.msg.access.dto.ReceiptCallback;
import com.msg.access.service.MessageService;
import com.msg.center.service.MessageCenterService;
import com.msg.center.service.ReceiptService;
import com.msg.common.dto.CancelRequest;
import com.msg.common.dto.CancelResult;
import com.msg.common.dto.DelaySendRequest;
import com.msg.common.dto.MessageStatusVO;
import com.msg.common.dto.SendRequest;
import com.msg.common.dto.SendResult;
import com.msg.common.dto.StatusQuery;
import org.springframework.stereotype.Service;

/**
 * 消息服务实现 — 接入层到核心层的委托桥接。
 * <p>
 * 将接入层的请求委托给消息中心（MessageCenterService）和回执服务（ReceiptService），
 * 完成 接入服务 → 消息中心 → RocketMQ → 渠道执行器 的全链路串联。
 * <p>
 * 需求: 1.1~1.6, 2.1~2.4, 5.1~5.3
 */
@Service
public class MessageServiceImpl implements MessageService {

    private final MessageCenterService messageCenterService;
    private final ReceiptService receiptService;

    public MessageServiceImpl(MessageCenterService messageCenterService,
                              ReceiptService receiptService) {
        this.messageCenterService = messageCenterService;
        this.receiptService = receiptService;
    }

    @Override
    public SendResult sendNow(SendRequest request) {
        return messageCenterService.processSendNow(request);
    }

    @Override
    public SendResult sendDelay(DelaySendRequest request) {
        return messageCenterService.processSendDelay(request);
    }

    @Override
    public MessageStatusVO queryStatus(StatusQuery query) {
        return messageCenterService.queryStatus(query);
    }

    @Override
    public CancelResult cancel(CancelRequest request) {
        return messageCenterService.cancelMessage(request);
    }

    @Override
    public void handleReceipt(ReceiptCallback receipt) {
        receiptService.processReceipt(receipt);
    }
}
