package com.msg.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 消息状态查询条件
 * 支持按 msgId 或 bizType+bizId 查询
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusQuery {

    /** 消息ID（按msgId查询时使用） */
    private String msgId;

    /** 业务类型（按业务键查询时使用） */
    private String bizType;

    /** 业务ID（按业务键查询时使用） */
    private String bizId;
}
