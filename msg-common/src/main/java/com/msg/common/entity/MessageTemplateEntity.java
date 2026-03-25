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
 * 消息模板表实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_msg_template")
public class MessageTemplateEntity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码(唯一) */
    private String templateCode;

    /** 模板名称 */
    private String templateName;

    /** 渠道类型 */
    private String channelType;

    /** 模板内容(Freemarker语法) */
    private String content;

    /** 模板变量说明(JSON) */
    private String variables;

    /** 是否启用 */
    private Boolean enabled;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
