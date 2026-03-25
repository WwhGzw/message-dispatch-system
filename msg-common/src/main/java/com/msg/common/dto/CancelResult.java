package com.msg.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 消息撤回结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否成功 */
    private boolean success;

    /** 结果描述 */
    private String message;

    public static CancelResult success() {
        return CancelResult.builder()
                .success(true)
                .message("撤回成功")
                .build();
    }

    public static CancelResult fail(String message) {
        return CancelResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}
