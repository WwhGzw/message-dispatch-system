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
 * 渠道配置表实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_channel_config")
public class ChannelConfigEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 渠道编码 */
    private String channelCode;

    /** 渠道类型: SMS/EMAIL/APP_PUSH/WEBHOOK */
    private String channelType;

    /** 渠道名称 */
    private String channelName;

    /** 渠道配置(JSON: appKey/secret/endpoint等) */
    private String config;

    /** 权重(负载均衡) */
    private Integer weight;

    /** QPS限制 */
    private Integer qpsLimit;

    /** 是否启用 */
    private Boolean enabled;

    /** 优先级 */
    private Integer priority;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
