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
 * 消息主表实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_msg")
public class MessageEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息ID(全局唯一，雪花算法) */
    private String msgId;

    /** 业务类型 */
    private String bizType;

    /** 业务ID */
    private String bizId;

    /** 渠道类型: SMS/EMAIL/APP_PUSH/WEBHOOK */
    private String channel;

    /** 消息模板编码 */
    private String templateCode;

    /** 渲染后的消息内容 */
    private String content;

    /** 接收人(手机号/邮箱/设备ID/URL) */
    private String receiver;

    /** 消息状态: PENDING/SENDING/SUCCESS/FAILED/RETRYING/DEAD_LETTER/CANCELLED */
    private String status;

    /** 已重试次数 */
    private Integer retryTimes;

    /** 最大重试次数 */
    private Integer maxRetryTimes;

    /** 计划发送时间(延迟消息) */
    private LocalDateTime sendTime;

    /** 实际发送时间 */
    private LocalDateTime actualSendTime;

    /** 优先级: 1-高 2-中 3-低 */
    private Integer priority;

    /** 扩展参数(JSON) */
    private String extParams;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
