package com.msg.access.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道回执回调 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptCallback {

    /** 消息ID */
    private String msgId;

    /** 渠道类型 */
    private String channel;

    /** 渠道方消息ID */
    private String channelMsgId;

    /** 回执状态: DELIVERED/READ/REJECTED/UNKNOWN */
    private String receiptStatus;

    /** 签名 */
    private String signature;

    /** 回执原始数据 */
    private String rawData;
}
