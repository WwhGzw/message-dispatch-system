package com.msg.executor.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道下发上下文 DTO
 * 包含渠道发送器执行下发所需的全部信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendContext {

    /** 消息ID */
    private String msgId;

    /** 渠道类型（SMS/EMAIL/APP_PUSH/WEBHOOK） */
    private String channel;

    /** 渲染后的消息内容 */
    private String content;

    /** 接收人（手机号/邮箱/设备ID/URL） */
    private String receiver;

    /** 渠道配置（JSON格式） */
    private String channelConfig;
}
