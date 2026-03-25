package com.msg.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 消息下发结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private String msgId;

    /** 消息状态 */
    private String status;

    /** 是否成功 */
    private boolean success;

    /** 结果描述 */
    private String message;

    public static SendResult success(String msgId) {
        return SendResult.builder()
                .msgId(msgId)
                .status("ACCEPTED")
                .success(true)
                .message("消息提交成功")
                .build();
    }

    public static SendResult idempotent(String msgId) {
        return SendResult.builder()
                .msgId(msgId)
                .status("DUPLICATE")
                .success(true)
                .message("幂等命中，返回已有消息")
                .build();
    }

    public static SendResult blocked(String reason) {
        return SendResult.builder()
                .success(false)
                .status("BLOCKED")
                .message(reason)
                .build();
    }

    public static SendResult fail(String message) {
        return SendResult.builder()
                .success(false)
                .status("FAILED")
                .message(message)
                .build();
    }
}
