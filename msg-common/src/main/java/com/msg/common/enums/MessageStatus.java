package com.msg.common.enums;

/**
 * 消息状态枚举
 * 状态机合法转换：
 * PENDING → SENDING | CANCELLED
 * SENDING → SUCCESS | FAILED | CANCELLED
 * FAILED → RETRYING | DEAD_LETTER
 * RETRYING → SENDING
 */
public enum MessageStatus {

    PENDING("待处理"),
    SENDING("发送中"),
    SUCCESS("发送成功"),
    FAILED("发送失败"),
    RETRYING("重试中"),
    DEAD_LETTER("死信"),
    CANCELLED("已撤回");

    private final String description;

    MessageStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 判断是否为终态（不可再转换）
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == DEAD_LETTER || this == CANCELLED;
    }
}
