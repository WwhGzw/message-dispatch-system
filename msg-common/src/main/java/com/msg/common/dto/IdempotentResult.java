package com.msg.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 幂等检查结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotentResult {

    /** 是否重复 */
    private boolean duplicate;

    /** 已存在的消息ID（重复时非空） */
    private String existingMsgId;

    /**
     * 幂等检查通过（非重复）
     */
    public static IdempotentResult pass() {
        return IdempotentResult.builder()
                .duplicate(false)
                .build();
    }

    /**
     * 幂等命中（重复消息）
     *
     * @param existingMsgId 已存在的消息ID
     */
    public static IdempotentResult duplicate(String existingMsgId) {
        return IdempotentResult.builder()
                .duplicate(true)
                .existingMsgId(existingMsgId)
                .build();
    }
}
