# 实现计划：消息下发系统（Message Delivery System）

## 概述

基于 SpringBoot 2.7.x + SpringCloud 微服务架构，以 RocketMQ 为消息中间件核心，分层实现消息下发系统。按照接入层 → 核心层 → 执行层 → 集成联调的顺序，逐步构建完整的消息分发平台。

## 任务

- [x] 1. 搭建项目基础结构与核心数据模型
  - [x] 1.1 创建 Maven 多模块项目结构，配置 SpringBoot 2.7.x、SpringCloud 2021.x、MyBatis-Plus 3.5.x、RocketMQ 4.9.x 等核心依赖
    - 创建 msg-common（公共模块）、msg-access（接入服务）、msg-center（消息中心）、msg-executor（渠道执行器）模块
    - 配置 application.yml 基础配置（数据源、Redis、RocketMQ 连接信息）
    - _需求: 1.1, 9.1_

  - [x] 1.2 定义核心枚举、常量与数据传输对象
    - 实现 `MessageStatus` 枚举（PENDING/SENDING/SUCCESS/FAILED/RETRYING/DEAD_LETTER/CANCELLED）
    - 实现 `ChannelType` 枚举（SMS/EMAIL/APP_PUSH/WEBHOOK）
    - 定义 `SendRequest`、`DelaySendRequest`、`SendResult`、`CancelRequest`、`CancelResult`、`StatusQuery`、`MessageStatusVO` 等 DTO
    - _需求: 1.1, 4.2_

  - [x] 1.3 创建数据库表结构与 MyBatis 实体映射
    - 创建 `t_msg`（消息主表）、`t_msg_receipt`（回执表）、`t_channel_config`（渠道配置表）、`t_msg_template`（模板表）的 DDL 脚本
    - 实现 `MessageEntity`、`MessageReceiptEntity`、`ChannelConfigEntity`、`MessageTemplateEntity` 实体类
    - 创建对应的 Mapper 接口，包含唯一索引 `uk_biz_channel(biz_type, biz_id, channel)` 和其他索引
    - _需求: 1.3, 3.3, 7.5_

- [x] 2. 实现消息状态机与幂等服务
  - [x] 2.1 实现消息状态机（StateMachine）
    - 定义合法状态转换集合：PENDING→SENDING、PENDING→CANCELLED、SENDING→SUCCESS、SENDING→FAILED、SENDING→CANCELLED、FAILED→RETRYING、FAILED→DEAD_LETTER、RETRYING→SENDING
    - 实现 `transitStatus(msgId, fromStatus, toStatus)` 方法，使用乐观锁（WHERE status = fromStatus）保证并发安全
    - 非法转换返回 false 且数据库无变更
    - _需求: 4.1, 4.3, 4.4_

  - [ ]* 2.2 编写状态机合法性属性测试
    - **Property 2: 状态机合法性**
    - 使用 jqwik 随机生成 (fromStatus, toStatus) 对，验证 transitStatus 返回 true 当且仅当该转换属于合法转换集合
    - **验证: 需求 4.1, 4.3**

  - [x] 2.3 实现幂等服务（IdempotentService）
    - 实现 Redis 分布式锁检查：key 为 `idem:{bizType}:{bizId}:{channel}`
    - 实现 DB 唯一索引二次校验逻辑
    - 捕获 `DuplicateKeyException` 返回幂等结果
    - Redis 不可用时降级到 DB 唯一索引保障
    - _需求: 3.1, 3.2, 3.3, 3.4, 12.3_

  - [ ]* 2.4 编写幂等性属性测试
    - **Property 1: 幂等性保证**
    - 使用 jqwik 随机生成 bizType+bizId+channel 组合，多次调用 processSendNow 验证返回相同 msgId 且 DB 仅一条记录
    - **验证: 需求 3.1, 3.2**

- [x] 3. 检查点 - 确保核心模型与状态机测试通过
  - 确保所有测试通过，如有疑问请向用户确认。

- [x] 4. 实现模板渲染与消息路由
  - [x] 4.1 实现模板渲染服务（TemplateRenderService）
    - 集成 Freemarker 引擎，实现 `renderTemplate(templateCode, variables)` 方法
    - 实现 Redis 缓存优先读取模板内容，缓存未命中时从 DB 加载并写入缓存
    - 渲染完成后校验结果中不包含未替换的占位符 `${...}`
    - 模板不存在/未启用时抛出 `TemplateRenderException`，变量缺失时抛出异常并记录详细错误
    - _需求: 7.1, 7.2, 7.3, 7.4, 7.5_

  - [ ]* 4.2 编写模板渲染完整性属性测试
    - **Property 7: 模板渲染完整性**
    - 使用 jqwik 随机生成合法模板和完整变量集，验证渲染结果不包含 `${...}` 占位符
    - **验证: 需求 7.1, 7.2**

  - [x] 4.3 实现消息路由器（MessageRouter）
    - 实现黑名单检查：命中则拦截并返回原因
    - 实现白名单模式检查：启用时仅白名单内可下发
    - 实现灰度规则检查：启用时未命中则拦截
    - 实现按优先级和权重选择可用渠道配置
    - 无可用渠道配置时返回拦截结果
    - _需求: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [ ]* 4.4 编写路由拦截完整性属性测试
    - **Property 6: 路由拦截完整性**
    - 使用 jqwik 随机生成接收人和黑白名单/灰度配置，验证拦截逻辑正确性
    - **验证: 需求 6.2, 6.3, 6.4**

  - [ ]* 4.5 编写渠道选择优先级属性测试
    - **Property 11: 渠道选择优先级**
    - 使用 jqwik 随机生成多个渠道配置（不同优先级），验证路由器返回优先级最高的已启用配置
    - **验证: 需求 6.5**

- [x] 5. 实现重试与死信处理
  - [x] 5.1 实现重试延迟计算（RetryHandler.calculateDelay）
    - 实现递增退避策略：10s→30s→1min→5min→30min→1h→2h→6h→12h
    - retryTimes 超出范围返回 -1
    - _需求: 8.2, 8.3_

  - [ ]* 5.2 编写重试延迟单调递增属性测试
    - **Property 4: 重试延迟单调递增**
    - 使用 jqwik 随机生成 (i, j) 对（1 ≤ i < j ≤ 9），验证 calculateDelay(i) < calculateDelay(j)
    - **验证: 需求 8.2, 8.3**

  - [x] 5.3 实现重试投递与死信处理逻辑
    - 实现 `submitRetry(msgId, retryTimes)`：投递到 RocketMQ 重试队列，设置延迟时间
    - 实现 `submitDeadLetter(msgId, reason)`：投递到死信队列，更新状态为 DEAD_LETTER
    - 保证 retryTimes 不超过 maxRetryTimes
    - _需求: 8.1, 8.4, 8.5_

  - [ ]* 5.4 编写重试次数有界性属性测试
    - **Property 3: 重试次数有界性**
    - 使用 jqwik 随机生成 retryTimes 和 maxRetryTimes，验证 retryTimes ≤ maxRetryTimes 且达到上限时进入终态
    - **验证: 需求 8.1, 8.5**

- [x] 6. 检查点 - 确保模板渲染、路由和重试逻辑测试通过
  - 确保所有测试通过，如有疑问请向用户确认。

- [x] 7. 实现接入服务与鉴权限流
  - [x] 7.1 实现 API 网关鉴权（HMAC-SHA256 签名验证）
    - 实现 AppKey + Secret 签名生成与验证逻辑
    - 鉴权失败返回 401 错误
    - _需求: 11.1, 11.2_

  - [ ]* 7.2 编写签名验证正确性属性测试
    - **Property 14: 签名验证正确性**
    - 使用 jqwik 随机生成 AppKey、Secret 和请求内容，验证正确密钥签名通过、错误密钥签名失败
    - **验证: 需求 11.1**

  - [x] 7.3 实现 Sentinel 多维度限流
    - 配置 AppKey 级、接口级、IP 级令牌桶限流规则
    - 超过限流阈值返回 429 错误
    - _需求: 11.3, 11.4_

  - [x] 7.4 实现接入服务 Controller 层
    - 实现 `POST /msg/send/now`（即时下发）、`POST /msg/send/delay`（延迟下发）接口
    - 实现 `GET /msg/status`（状态查询）、`POST /msg/cancel`（消息撤回）接口
    - 实现 `POST /msg/callback/receipt`（渠道回执回调）接口
    - 请求参数校验（JSR303 + 自定义校验）
    - _需求: 1.1, 2.1, 4.2, 5.1, 10.1_

- [x] 8. 实现消息下发主流程（消息中心核心逻辑）
  - [x] 8.1 实现即时消息下发流程（processSendNow）
    - 集成幂等检查 → 雪花算法生成 msgId → 模板渲染 → 消息路由 → 持久化（PENDING）→ RocketMQ 事务消息投递 → 状态更新（SENDING）
    - Redis 不可用时降级到 DB 唯一索引
    - MQ 投递失败时保持 PENDING 状态并返回失败
    - _需求: 1.1, 1.2, 1.3, 1.4, 3.1, 3.2, 3.3, 12.2_

  - [ ]* 8.2 编写 msgId 全局唯一性属性测试
    - **Property 9: msgId 全局唯一性**
    - 使用 jqwik 批量生成 msgId，验证所有 ID 互不相同
    - **验证: 需求 1.2**

  - [x] 8.3 实现延迟/定时消息下发流程
    - 短延迟：通过 RocketMQ 延迟消息机制投递
    - 长延迟/精确定时：持久化到 DB，由 XXL-Job 定时任务扫描到期消息投递到 MQ
    - 校验 sendTime 合法性
    - _需求: 2.1, 2.2, 2.3, 2.4_

  - [x] 8.4 实现消息撤回逻辑
    - 校验目标消息是否处于可撤回状态（PENDING 或 SENDING）
    - 调用状态机将状态转换为 CANCELLED
    - 已处于终态时拒绝撤回并返回失败原因
    - _需求: 5.1, 5.2, 5.3_

  - [x] 8.5 实现消息状态查询
    - 支持按 msgId 或 bizType+bizId 查询
    - 返回当前状态、重试次数和实际发送时间
    - _需求: 4.2_

- [x] 9. 实现渠道执行器与多渠道抽象
  - [x] 9.1 实现 ChannelSender 接口与渠道发送器注册机制
    - 定义 `ChannelSender` 接口（getChannelType/send/healthCheck）
    - 实现 `ChannelExecutor` 渠道执行引擎，基于策略模式自动路由到对应渠道发送器
    - 新渠道发送器注册后自动纳入路由范围（Spring 自动注入）
    - 无对应渠道发送器时投递到死信队列
    - _需求: 9.1, 9.2, 9.3, 9.4_

  - [ ]* 9.2 编写渠道路由正确性属性测试
    - **Property 10: 渠道路由正确性**
    - 使用 jqwik 随机生成消息 channel 和已注册的渠道发送器集合，验证路由到 getChannelType() 匹配的实现
    - **验证: 需求 9.2**

  - [x] 9.3 实现 MQ 消费与渠道执行流程
    - 消费 RocketMQ 消息，执行消费端幂等校验（终态消息跳过）
    - 调用渠道发送器执行下发
    - 成功时更新状态为 SUCCESS 并记录实际发送时间
    - 失败时进入重试/死信处理流程
    - 渠道 API 超时（>5s）捕获异常进入重试
    - _需求: 1.5, 1.6, 3.5, 8.1, 12.1_

  - [ ]* 9.4 编写消费端幂等性属性测试
    - **Property 8: 消费端幂等性**
    - 使用 jqwik 随机生成终态消息，验证渠道执行器跳过处理不调用渠道 API
    - **验证: 需求 3.5**

  - [x] 9.5 实现具体渠道发送器（SMS/EMAIL/APP_PUSH/WEBHOOK）
    - 实现 `SmsChannelSender`、`EmailChannelSender`、`AppPushChannelSender`、`WebHookChannelSender`
    - 每个实现包含 getChannelType()、send()、healthCheck() 方法
    - 配置渠道 API 调用超时为 5 秒
    - _需求: 9.1, 12.1_

- [x] 10. 检查点 - 确保消息下发全流程与渠道执行测试通过
  - 确保所有测试通过，如有疑问请向用户确认。

- [x] 11. 实现渠道回执处理与数据安全
  - [x] 11.1 实现渠道回执回调处理
    - 验证回执签名合法性
    - 签名通过后插入回执记录（t_msg_receipt）并更新消息最终状态
    - 签名失败拒绝请求
    - _需求: 10.1, 10.2, 10.3_

  - [x] 11.2 实现数据安全与脱敏
    - 实现 AES-256 加密/解密工具类，用于渠道配置中 Secret/Token 的加密存储与运行时解密
    - 实现日志脱敏拦截器，对手机号、邮箱等敏感信息脱敏输出
    - 确保 MyBatis 使用参数化查询，禁止 SQL 拼接
    - _需求: 13.1, 13.2, 13.4_

  - [ ]* 11.3 编写密钥加密 Round-Trip 属性测试
    - **Property 12: 密钥加密 Round-Trip**
    - 使用 jqwik 随机生成 Secret/Token 字符串，验证加密后解密得到原始值
    - **验证: 需求 13.1**

  - [ ]* 11.4 编写日志脱敏完整性属性测试
    - **Property 13: 日志脱敏完整性**
    - 使用 jqwik 随机生成包含手机号/邮箱的日志内容，验证脱敏后不包含完整敏感信息
    - **验证: 需求 13.2**

- [x] 12. 实现错误处理与降级机制
  - [x] 12.1 实现渠道熔断与降级
    - 配置 Sentinel 熔断规则：渠道连续超时触发熔断
    - Redis 不可用时自动降级到 DB 幂等保障和直接 DB 查询
    - 模板渲染失败时消息状态直接置为 FAILED 不进入重试
    - _需求: 12.1, 12.3, 12.4, 12.5_

  - [x] 12.2 实现全局异常处理与统一响应
    - 实现 `@ControllerAdvice` 全局异常处理器
    - 统一错误码与响应格式
    - RocketMQ 投递失败保持 PENDING 状态并返回失败
    - _需求: 12.2_

- [x] 13. 集成联调与端到端验证
  - [x] 13.1 配置 XXL-Job 定时任务
    - 实现定时扫描到期 PENDING 消息并投递到 MQ 的任务
    - 配置扫描间隔和批次大小
    - _需求: 2.3, 2.4_

  - [x] 13.2 集成所有组件并完成端到端联调
    - 接入服务 → 消息中心 → RocketMQ → 渠道执行器全链路联调
    - 验证即时下发、延迟下发、消息撤回、状态查询、回执处理完整流程
    - 验证重试队列 → 死信队列完整流转
    - _需求: 1.1~1.6, 2.1~2.4, 5.1~5.3, 8.1~8.5_

  - [ ]* 13.3 编写集成测试
    - 使用 Testcontainers 搭建 RocketMQ 和 MySQL 测试环境
    - 使用 Embedded Redis 模拟 Redis 环境
    - 编写全链路集成测试用例
    - _需求: 1.1~1.6, 8.1~8.5_

- [x] 14. 最终检查点 - 确保所有测试通过
  - 确保所有测试通过，如有疑问请向用户确认。

## 备注

- 标记 `*` 的子任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号，确保需求可追溯
- 检查点任务用于阶段性验证，确保增量开发的正确性
- 属性测试使用 jqwik 框架验证系统的通用正确性属性
- 单元测试验证具体示例和边界条件
