package com.msg.access.service;

import com.msg.access.dto.ReceiptCallback;
import com.msg.common.dto.CancelRequest;
import com.msg.common.dto.CancelResult;
import com.msg.common.dto.DelaySendRequest;
import com.msg.common.dto.MessageStatusVO;
import com.msg.common.dto.SendRequest;
import com.msg.common.dto.SendResult;
import com.msg.common.dto.StatusQuery;

/**
 * 消息服务接口 — 接入层业务委托
 * 具体实现将在后续任务中完成
 */
public interface MessageService {

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
