package com.msg.executor.channel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道下发结果 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendChannelResult {

    /** 是否下发成功 */
    private boolean success;

    /** 渠道方消息ID（成功时返回） */
    private String channelMsgId;

    /** 错误信息（失败时返回） */
    private String errorMessage;

    /**
     * 创建成功结果
     */
    public static SendChannelResult success(String channelMsgId) {
        return new SendChannelResult(true, channelMsgId, null);
    }

    /**
     * 创建失败结果
     */
    public static SendChannelResult fail(String errorMessage) {
        return new SendChannelResult(false, null, errorMessage);
    }
}
