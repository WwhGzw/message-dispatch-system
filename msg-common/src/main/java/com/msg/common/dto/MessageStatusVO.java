package com.msg.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息状态视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageStatusVO {

    /** 消息ID */
    private String msgId;

    /** 当前状态 */
    private String status;

    /** 已重试次数 */
    private Integer retryTimes;

    /** 实际发送时间 */
    private LocalDateTime actualSendTime;
}
