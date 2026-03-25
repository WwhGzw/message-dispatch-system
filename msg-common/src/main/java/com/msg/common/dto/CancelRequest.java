package com.msg.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

/**
 * 消息撤回请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRequest {

    /** 消息ID */
    @NotBlank(message = "msgId不能为空")
    private String msgId;

    /** 撤回原因 */
    private String reason;
}
