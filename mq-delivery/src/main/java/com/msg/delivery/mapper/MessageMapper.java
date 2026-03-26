package com.msg.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msg.delivery.entity.MessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Message Mapper Interface
 * 
 * MyBatis-Plus mapper for MessageEntity providing CRUD operations
 * and query capabilities for message records.
 * 
 * Extends BaseMapper to inherit standard CRUD methods:
 * - insert(entity)
 * - deleteById(id)
 * - updateById(entity)
 * - selectById(id)
 * - selectList(queryWrapper)
 * - selectPage(page, queryWrapper)
 * 
 * @author MQ Delivery System
 */
@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {
    // BaseMapper provides all standard CRUD operations
    // Custom query methods can be added here if needed
}
