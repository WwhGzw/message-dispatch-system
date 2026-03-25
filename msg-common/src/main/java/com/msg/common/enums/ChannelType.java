package com.msg.common.enums;

/**
 * 渠道类型枚举
 */
public enum ChannelType {

    SMS("短信"),
    EMAIL("邮件"),
    APP_PUSH("App推送"),
    WEBHOOK("WebHook回调");

    private final String description;

    ChannelType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
