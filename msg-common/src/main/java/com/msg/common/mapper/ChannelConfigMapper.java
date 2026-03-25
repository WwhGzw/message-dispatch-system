package com.msg.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msg.common.entity.ChannelConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 渠道配置表 Mapper
 */
@Mapper
public interface ChannelConfigMapper extends BaseMapper<ChannelConfigEntity> {

    /**
     * 查询指定渠道类型下所有已启用的配置，按优先级排序
     */
    @Select("SELECT * FROM t_channel_config WHERE channel_type = #{channelType} AND enabled = 1 ORDER BY priority ASC")
    List<ChannelConfigEntity> selectEnabledByType(@Param("channelType") String channelType);
}
