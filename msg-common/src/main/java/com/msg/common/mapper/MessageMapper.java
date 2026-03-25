package com.msg.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msg.common.entity.MessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 消息主表 Mapper
 *
 * 安全说明：所有 SQL 均使用 MyBatis @Select/@Update 注解的参数化查询（#{param}），
 * 禁止使用 ${} 拼接 SQL，以防止 SQL 注入攻击。（需求 13.4）
 */
@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {

    /**
     * 根据 msgId 查询消息
     */
    @Select("SELECT * FROM t_msg WHERE msg_id = #{msgId}")
    MessageEntity selectByMsgId(@Param("msgId") String msgId);

    /**
     * 根据业务键查询消息（幂等校验）
     */
    @Select("SELECT * FROM t_msg WHERE biz_type = #{bizType} AND biz_id = #{bizId} AND channel = #{channel}")
    MessageEntity selectByBizKey(@Param("bizType") String bizType,
                                 @Param("bizId") String bizId,
                                 @Param("channel") String channel);

    /**
     * 乐观锁更新消息状态
     *
     * @return 影响行数，0 表示状态已变更（乐观锁失败）
     */
    @Update("UPDATE t_msg SET status = #{toStatus}, update_time = NOW() " +
            "WHERE msg_id = #{msgId} AND status = #{fromStatus}")
    int updateStatus(@Param("msgId") String msgId,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);

    /**
     * 重试次数 +1
     */
    @Update("UPDATE t_msg SET retry_times = retry_times + 1, update_time = NOW() " +
            "WHERE msg_id = #{msgId}")
    int incrementRetryTimes(@Param("msgId") String msgId);

    /**
     * 更新实际发送时间
     */
    @Update("UPDATE t_msg SET actual_send_time = #{actualSendTime}, update_time = NOW() " +
            "WHERE msg_id = #{msgId}")
    int updateActualSendTime(@Param("msgId") String msgId,
                             @Param("actualSendTime") java.time.LocalDateTime actualSendTime);

    /**
     * 根据 bizType + bizId 查询消息（取第一条匹配记录）
     */
    @Select("SELECT * FROM t_msg WHERE biz_type = #{bizType} AND biz_id = #{bizId} LIMIT 1")
    MessageEntity selectByBizTypeAndBizId(@Param("bizType") String bizType,
                                          @Param("bizId") String bizId);

    /**
     * 查询到期的 PENDING 消息（send_time <= now），按 send_time 升序，限制批次大小。
     * 用于 XXL-Job / 定时任务扫描长延迟消息并投递到 MQ。
     */
    @Select("SELECT * FROM t_msg WHERE status = 'PENDING' AND send_time <= #{now} " +
            "ORDER BY send_time ASC LIMIT #{batchSize}")
    java.util.List<MessageEntity> selectPendingExpiredMessages(@Param("now") java.time.LocalDateTime now,
                                                               @Param("batchSize") int batchSize);
}
