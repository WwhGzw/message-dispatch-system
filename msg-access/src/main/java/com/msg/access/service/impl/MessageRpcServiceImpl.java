package com.msg.access.service.impl;

import com.msg.center.service.MessageCenterService;
import com.msg.center.service.ReceiptService;
import com.msg.common.api.MessageRpcService;
import com.msg.common.dto.*;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 消息下发 Dubbo RPC 服务实现
 * <p>
 * 通过 Dubbo 协议对外暴露 RPC 接口，业务方通过 @DubboReference 注入调用。
 * 内部委托给 MessageCenterService 和 ReceiptService 处理。
 */
@DubboService(version = "1.0.0", timeout = 5000)
public class MessageRpcServiceImpl implements MessageRpcService {

    private static final Logger log = LoggerFactory.getLogger(MessageRpcServiceImpl.class);

    private final MessageCenterService messageCenterService;
    private final ReceiptService receiptService;

    public MessageRpcServiceImpl(MessageCenterService messageCenterService,
                                 ReceiptService receiptService) {
        this.messageCenterService = messageCenterService;
        this.receiptService = receiptService;
    }

    @Override
    public SendResult sendNow(SendRequest request) {
        log.info("RPC sendNow: bizType={}, bizId={}, channel={}",
                request.getBizType(), request.getBizId(), request.getChannel());
        return messageCenterService.processSendNow(request);
    }

    @Override
    public SendResult sendDelay(DelaySendRequest request) {
        log.info("RPC sendDelay: bizType={}, bizId={}, channel={}, sendTime={}",
                request.getBizType(), request.getBizId(), request.getChannel(), request.getSendTime());
        return messageCenterService.processSendDelay(request);
    }

    @Override
    public MessageStatusVO queryStatus(StatusQuery query) {
        log.info("RPC queryStatus: msgId={}, bizType={}, bizId={}",
                query.getMsgId(), query.getBizType(), query.getBizId());
        return messageCenterService.queryStatus(query);
    }

    @Override
    public CancelResult cancel(CancelRequest request) {
        log.info("RPC cancel: msgId={}", request.getMsgId());
        return messageCenterService.cancelMessage(request);
    }

    @Override
    public void handleReceipt(ReceiptCallback receipt) {
        log.info("RPC handleReceipt: msgId={}, receiptStatus={}",
                receipt.getMsgId(), receipt.getReceiptStatus());
        receiptService.processReceipt(receipt);
    }
}
