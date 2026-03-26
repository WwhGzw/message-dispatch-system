package com.msg.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msg.delivery.entity.ReceiptEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Receipt Mapper Interface
 * 
 * MyBatis-Plus mapper for ReceiptEntity providing CRUD operations
 * and query capabilities for receipt records.
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
public interface ReceiptMapper extends BaseMapper<ReceiptEntity> {
    // BaseMapper provides all standard CRUD operations
    // Custom query methods can be added here if needed
}
