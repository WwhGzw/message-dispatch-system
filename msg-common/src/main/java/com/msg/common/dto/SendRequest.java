package com.msg.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Map;

/**
 * 即时消息下发请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务类型 */
    @NotBlank(message = "bizType不能为空")
    private String bizType;

    /** 业务ID */
    @NotBlank(message = "bizId不能为空")
    private String bizId;

    /** 渠道类型 */
    @NotBlank(message = "channel不能为空")
    private String channel;

    /** 消息模板编码 */
    @NotBlank(message = "templateCode不能为空")
    private String templateCode;

    /** 接收人（手机号/邮箱/设备ID/URL） */
    @NotBlank(message = "receiver不能为空")
    private String receiver;

    /** 模板变量 */
    private Map<String, Object> variables;

    /** 优先级: 1-高 2-中 3-低，默认2 */
    @Builder.Default
    private Integer priority = 2;
}
