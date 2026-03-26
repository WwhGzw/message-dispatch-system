package com.msg.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msg.delivery.entity.DeliveryAttemptEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Delivery Attempt Mapper Interface
 * 
 * MyBatis-Plus mapper for DeliveryAttemptEntity providing CRUD operations
 * and query capabilities for delivery attempt records.
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
public interface DeliveryAttemptMapper extends BaseMapper<DeliveryAttemptEntity> {
    // BaseMapper provides all standard CRUD operations
    // Custom query methods can be added here if needed
}
