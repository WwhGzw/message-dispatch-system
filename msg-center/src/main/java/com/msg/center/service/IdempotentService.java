package com.msg.center.service;

import com.msg.common.dto.IdempotentResult;
import com.msg.common.entity.MessageEntity;
import com.msg.common.mapper.MessageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 幂等服务
 * <p>
 * 三级幂等保障：
 * 1. Redis 分布式锁（SETNX）快速检查
 * 2. DB 唯一索引二次校验
 * 3. DuplicateKeyException 兜底
 * <p>
 * Redis 不可用时自动降级到 DB 唯一索引保障。
 */
@Service
public class IdempotentService {

    private static final Logger log = LoggerFactory.getLogger(IdempotentService.class);

    private static final String KEY_PREFIX = "idem:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private MessageMapper messageMapper;

    /**
     * 幂等检查并加锁
     * <p>
     * 流程：
     * 1. 尝试 Redis SETNX 获取分布式锁
     * 2. 若 Redis 不可用，降级到 DB 校验
     * 3. 若 Redis key 已存在，查询 DB 返回重复结果
     * 4. 若 Redis 锁获取成功，再查 DB 二次校验（防止 Redis 锁过期后的并发窗口）
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @param channel 渠道
     * @return 幂等检查结果
     */
    public IdempotentResult checkAndLock(String bizType, String bizId, String channel) {
        String redisKey = buildKey(bizType, bizId, channel);

        // Step 1: 尝试 Redis SETNX
        boolean redisAvailable = true;
        boolean locked = false;
        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "1", LOCK_TTL);
            locked = Boolean.TRUE.equals(result);
        } catch (Exception e) {
            // Redis 不可用，降级到 DB 唯一索引保障
            log.warn("Redis 不可用，降级到 DB 幂等校验. key={}", redisKey, e);
            redisAvailable = false;
        }

        // Step 2: Redis 不可用 → 仅依赖 DB 唯一索引
        if (!redisAvailable) {
            return checkByDb(bizType, bizId, channel);
        }

        // Step 3: Redis key 已存在 → 重复请求
        if (!locked) {
            MessageEntity existing = messageMapper.selectByBizKey(bizType, bizId, channel);
            if (existing != null) {
                return IdempotentResult.duplicate(existing.getMsgId());
            }
            // Redis key 存在但 DB 无记录（可能前一个请求还在处理中）
            // 返回 pass 让调用方继续，DB 唯一索引会兜底
            return IdempotentResult.pass();
        }

        // Step 4: Redis 锁获取成功 → DB 二次校验
        return checkByDb(bizType, bizId, channel);
    }

    /**
     * 释放 Redis 分布式锁
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @param channel 渠道
     */
    public void releaseLock(String bizType, String bizId, String channel) {
        String redisKey = buildKey(bizType, bizId, channel);
        try {
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("释放 Redis 锁失败. key={}", redisKey, e);
        }
    }

    /**
     * 处理 DuplicateKeyException 场景
     * <p>
     * 当 DB 插入时发生唯一索引冲突，查询已有记录返回幂等结果。
     *
     * @param bizType 业务类型
     * @param bizId   业务ID
     * @param channel 渠道
     * @return 幂等结果（duplicate）
     */
    public IdempotentResult handleDuplicateKey(String bizType, String bizId, String channel) {
        MessageEntity existing = messageMapper.selectByBizKey(bizType, bizId, channel);
        if (existing != null) {
            return IdempotentResult.duplicate(existing.getMsgId());
        }
        // 极端情况：记录被删除，返回 pass 让调用方重新处理
        return IdempotentResult.pass();
    }

    /**
     * DB 查询校验
     */
    private IdempotentResult checkByDb(String bizType, String bizId, String channel) {
        MessageEntity existing = messageMapper.selectByBizKey(bizType, bizId, channel);
        if (existing != null) {
            return IdempotentResult.duplicate(existing.getMsgId());
        }
        return IdempotentResult.pass();
    }

    /**
     * 构建 Redis 幂等 key
     */
    private String buildKey(String bizType, String bizId, String channel) {
        return KEY_PREFIX + bizType + ":" + bizId + ":" + channel;
    }
}
