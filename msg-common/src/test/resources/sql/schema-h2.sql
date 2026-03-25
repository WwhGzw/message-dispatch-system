-- ============================================================
-- 消息下发系统 DDL（H2 兼容，用于单元/集成测试）
-- ============================================================

-- 消息主表
CREATE TABLE IF NOT EXISTS t_msg (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    msg_id          VARCHAR(64)  NOT NULL,
    biz_type        VARCHAR(64)  NOT NULL,
    biz_id          VARCHAR(128) NOT NULL,
    channel         VARCHAR(32)  NOT NULL,
    template_code   VARCHAR(64)  DEFAULT NULL,
    content         CLOB         DEFAULT NULL,
    receiver        VARCHAR(256) NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    retry_times     INT          NOT NULL DEFAULT 0,
    max_retry_times INT          NOT NULL DEFAULT 10,
    send_time       TIMESTAMP    DEFAULT NULL,
    actual_send_time TIMESTAMP   DEFAULT NULL,
    priority        INT          NOT NULL DEFAULT 2,
    ext_params      CLOB         DEFAULT NULL,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_biz_channel ON t_msg (biz_type, biz_id, channel);
CREATE INDEX IF NOT EXISTS idx_msg_id ON t_msg (msg_id);
CREATE INDEX IF NOT EXISTS idx_status_send_time ON t_msg (status, send_time);
CREATE INDEX IF NOT EXISTS idx_create_time ON t_msg (create_time);

-- 消息回执表
CREATE TABLE IF NOT EXISTS t_msg_receipt (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    msg_id          VARCHAR(64)  NOT NULL,
    channel         VARCHAR(32)  NOT NULL,
    channel_msg_id  VARCHAR(128) DEFAULT NULL,
    receipt_status  VARCHAR(32)  NOT NULL,
    receipt_time    TIMESTAMP    DEFAULT NULL,
    raw_data        CLOB         DEFAULT NULL,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_receipt_msg_id ON t_msg_receipt (msg_id);


-- 渠道配置表
CREATE TABLE IF NOT EXISTS t_channel_config (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    channel_code    VARCHAR(64)  NOT NULL,
    channel_type    VARCHAR(32)  NOT NULL,
    channel_name    VARCHAR(128) NOT NULL,
    config          CLOB         DEFAULT NULL,
    weight          INT          NOT NULL DEFAULT 1,
    qps_limit       INT          NOT NULL DEFAULT 100,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    priority        INT          NOT NULL DEFAULT 0,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_channel_code ON t_channel_config (channel_code);
CREATE INDEX IF NOT EXISTS idx_channel_type_enabled ON t_channel_config (channel_type, enabled);

-- 消息模板表
CREATE TABLE IF NOT EXISTS t_msg_template (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    template_code   VARCHAR(64)  NOT NULL,
    template_name   VARCHAR(128) NOT NULL,
    channel_type    VARCHAR(32)  NOT NULL,
    content         CLOB         NOT NULL,
    variables       CLOB         DEFAULT NULL,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_template_code ON t_msg_template (template_code);
