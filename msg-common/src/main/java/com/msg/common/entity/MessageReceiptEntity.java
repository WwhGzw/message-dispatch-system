package com.msg.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 消息回执表实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_msg_receipt")
public class MessageReceiptEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联消息ID */
    private String msgId;

    /** 渠道类型 */
    private String channel;

    /** 渠道方消息ID */
    private String channelMsgId;

    /** 回执状态: DELIVERED/READ/REJECTED/UNKNOWN */
    private String receiptStatus;

    /** 回执时间 */
    private LocalDateTime receiptTime;

    /** 回执原始数据(JSON) */
    private String rawData;

    /** 创建时间 */
    private LocalDateTime createTime;
}
