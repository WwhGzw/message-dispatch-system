package com.msg.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息撤回结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelResult {

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
