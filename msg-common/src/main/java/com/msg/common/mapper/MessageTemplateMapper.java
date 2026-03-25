package com.msg.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msg.common.entity.MessageTemplateEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 消息模板表 Mapper
 */
@Mapper
public interface MessageTemplateMapper extends BaseMapper<MessageTemplateEntity> {

    /**
     * 根据模板编码查询模板
     */
    @Select("SELECT * FROM t_msg_template WHERE template_code = #{templateCode}")
    MessageTemplateEntity selectByTemplateCode(@Param("templateCode") String templateCode);
}
