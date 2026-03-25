package com.msg.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msg.common.entity.MessageReceiptEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息回执表 Mapper
 */
@Mapper
public interface MessageReceiptMapper extends BaseMapper<MessageReceiptEntity> {
}
