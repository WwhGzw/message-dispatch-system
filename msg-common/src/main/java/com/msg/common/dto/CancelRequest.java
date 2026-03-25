package com.msg.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

import java.io.Serializable;

/**
 * 消息撤回请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息ID */
    @NotBlank(message = "msgId不能为空")
    private String msgId;

    /** 撤回原因 */
    private String reason;
}
