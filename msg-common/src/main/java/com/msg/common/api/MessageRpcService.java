package com.msg.common.api;

import com.msg.common.dto.*;

/**
 * 消息下发系统 Dubbo RPC 接口
 * <p>
 * 由 msg-access 模块提供实现（@DubboService），
 * 业务方通过 msg-common 依赖引入此接口，使用 @DubboReference 注入调用。
 */
public interface MessageRpcService {

    /**
     * 即时消息下发
     */
    SendResult sendNow(SendRequest request);

    /**
     * 延迟/定时消息下发
     */
    SendResult sendDelay(DelaySendRequest request);

    /**
     * 消息状态查询
     */
    MessageStatusVO queryStatus(StatusQuery query);

    /**
     * 消息撤回
     */
    CancelResult cancel(CancelRequest request);

    /**
     * 处理渠道回执回调
     */
    void handleReceipt(ReceiptCallback receipt);
}
