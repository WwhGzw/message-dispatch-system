-- MQ Delivery System Database Schema

-- Message Table
CREATE TABLE IF NOT EXISTS t_mq_message (
    message_id      VARCHAR(64)  PRIMARY KEY COMMENT 'Unique message identifier',
    destination_url VARCHAR(2048) NOT NULL COMMENT 'Downstream HTTP endpoint',
    payload         TEXT         NOT NULL COMMENT 'Message payload (JSON)',
    status          VARCHAR(32)  NOT NULL COMMENT 'PENDING/DELIVERED/FAILED/DEAD_LETTER',
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT 'Current retry attempt',
    max_retries     INT          NOT NULL DEFAULT 5 COMMENT 'Maximum retry attempts',
    failure_reason  VARCHAR(1024)         COMMENT 'Failure reason if failed',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    delivery_time   DATETIME              COMMENT 'Delivery completion timestamp',
    
    INDEX idx_status (status),
    INDEX idx_create_time (create_time),
    INDEX idx_status_create_time (status, create_time)
) COMMENT 'MQ message delivery records';

-- Delivery Attempt Table
CREATE TABLE IF NOT EXISTS t_mq_delivery_attempt (
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY,
    message_id      VARCHAR(64)  NOT NULL COMMENT 'Associated message ID',
    attempt_number  INT          NOT NULL COMMENT 'Attempt number (1-based)',
    http_status     INT                   COMMENT 'HTTP status code',
    response_body   TEXT                  COMMENT 'Response body from downstream',
    delivery_result VARCHAR(32)  NOT NULL COMMENT 'SUCCESS/TIMEOUT/CONNECTION_ERROR/HTTP_ERROR',
    error_message   VARCHAR(1024)         COMMENT 'Error message if failed',
    attempt_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    latency_ms      BIGINT                COMMENT 'Delivery latency in milliseconds',
    
    INDEX idx_message_id (message_id),
    INDEX idx_attempt_time (attempt_time)
) COMMENT 'Message delivery attempt history';

-- Receipt Table
CREATE TABLE IF NOT EXISTS t_mq_receipt (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY,
    message_id   VARCHAR(64)  NOT NULL COMMENT 'Original message identifier',
    receipt_data TEXT         NOT NULL COMMENT 'Receipt data from downstream (JSON)',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consumed     BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'Whether consumed by upstream',
    consume_time DATETIME              COMMENT 'Receipt consumption timestamp',
    
    INDEX idx_message_id (message_id),
    INDEX idx_consumed (consumed),
    INDEX idx_create_time (create_time)
) COMMENT 'Message delivery receipts';
