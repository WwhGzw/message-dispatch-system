-- ============================================================
-- 消息下发系统 DDL（MySQL 8.0）
-- ============================================================

-- 消息主表
CREATE TABLE IF NOT EXISTS `t_msg` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `msg_id`          VARCHAR(64)  NOT NULL COMMENT '消息ID(全局唯一，雪花算法)',
    `biz_type`        VARCHAR(64)  NOT NULL COMMENT '业务类型',
    `biz_id`          VARCHAR(128) NOT NULL COMMENT '业务ID',
    `channel`         VARCHAR(32)  NOT NULL COMMENT '渠道类型: SMS/EMAIL/APP_PUSH/WEBHOOK',
    `template_code`   VARCHAR(64)  DEFAULT NULL COMMENT '消息模板编码',
    `content`         TEXT         DEFAULT NULL COMMENT '渲染后的消息内容',
    `receiver`        VARCHAR(256) NOT NULL COMMENT '接收人(手机号/邮箱/设备ID/URL)',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '消息状态: PENDING/SENDING/SUCCESS/FAILED/RETRYING/DEAD_LETTER/CANCELLED',
    `retry_times`     INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `max_retry_times` INT          NOT NULL DEFAULT 10 COMMENT '最大重试次数',
    `send_time`       DATETIME     DEFAULT NULL COMMENT '计划发送时间(延迟消息)',
    `actual_send_time` DATETIME    DEFAULT NULL COMMENT '实际发送时间',
    `priority`        INT          NOT NULL DEFAULT 2 COMMENT '优先级: 1-高 2-中 3-低',
    `ext_params`      TEXT         DEFAULT NULL COMMENT '扩展参数(JSON)',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_biz_channel` (`biz_type`, `biz_id`, `channel`),
    KEY `idx_msg_id` (`msg_id`),
    KEY `idx_status_send_time` (`status`, `send_time`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息主表';

-- 消息回执表
CREATE TABLE IF NOT EXISTS `t_msg_receipt` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `msg_id`          VARCHAR(64)  NOT NULL COMMENT '关联消息ID',
    `channel`         VARCHAR(32)  NOT NULL COMMENT '渠道类型',
    `channel_msg_id`  VARCHAR(128) DEFAULT NULL COMMENT '渠道方消息ID',
    `receipt_status`  VARCHAR(32)  NOT NULL COMMENT '回执状态: DELIVERED/READ/REJECTED/UNKNOWN',
    `receipt_time`    DATETIME     DEFAULT NULL COMMENT '回执时间',
    `raw_data`        TEXT         DEFAULT NULL COMMENT '回执原始数据(JSON)',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_receipt_msg_id` (`msg_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息回执表';


-- 渠道配置表
CREATE TABLE IF NOT EXISTS `t_channel_config` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `channel_code`    VARCHAR(64)  NOT NULL COMMENT '渠道编码',
    `channel_type`    VARCHAR(32)  NOT NULL COMMENT '渠道类型: SMS/EMAIL/APP_PUSH/WEBHOOK',
    `channel_name`    VARCHAR(128) NOT NULL COMMENT '渠道名称',
    `config`          TEXT         DEFAULT NULL COMMENT '渠道配置(JSON: appKey/secret/endpoint等)',
    `weight`          INT          NOT NULL DEFAULT 1 COMMENT '权重(负载均衡)',
    `qps_limit`       INT          NOT NULL DEFAULT 100 COMMENT 'QPS限制',
    `enabled`         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `priority`        INT          NOT NULL DEFAULT 0 COMMENT '优先级',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_code` (`channel_code`),
    KEY `idx_channel_type_enabled` (`channel_type`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道配置表';

-- 消息模板表
CREATE TABLE IF NOT EXISTS `t_msg_template` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `template_code`   VARCHAR(64)  NOT NULL COMMENT '模板编码(唯一)',
    `template_name`   VARCHAR(128) NOT NULL COMMENT '模板名称',
    `channel_type`    VARCHAR(32)  NOT NULL COMMENT '渠道类型',
    `content`         TEXT         NOT NULL COMMENT '模板内容(Freemarker语法)',
    `variables`       TEXT         DEFAULT NULL COMMENT '模板变量说明(JSON)',
    `enabled`         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息模板表';
