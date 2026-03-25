# 需求文档

## 简介

消息下发系统（Message Delivery System）是一个面向生产环境的高可靠、高并发消息分发平台，统一承接系统内通知、告警、运营推送、第三方回调等多种消息下发场景。系统基于微服务架构，以 RocketMQ 作为消息中间件核心，实现消息的可靠投递、多渠道统一抽象、全链路状态追踪与灰度管控。

## 术语表

- **接入服务（AccessService）**：统一接收外部下发请求的服务组件，负责鉴权、参数校验和 QPS 限流
- **消息中心（MessageCenter）**：消息处理核心组件，负责幂等校验、路由决策、模板渲染和状态管理
- **渠道执行器（ChannelExecutor）**：统一抽象多渠道下发的执行组件，消费 MQ 消息并调用具体渠道 API
- **重试处理器（RetryHandler）**：管理消息重试策略与死信队列处理的组件
- **消息路由器（MessageRouter）**：根据优先级、灰度规则、黑白名单进行渠道路由决策的组件
- **渠道发送器（ChannelSender）**：具体渠道（短信/邮件/AppPush/WebHook）的发送实现抽象接口
- **幂等键（IdempotentKey）**：由 bizType + bizId + channel 组成的唯一标识，用于防止重复下发
- **状态机（StateMachine）**：管理消息生命周期状态流转的机制，合法状态包括 PENDING、SENDING、SUCCESS、FAILED、RETRYING、DEAD_LETTER、CANCELLED
- **死信队列（DeadLetterQueue）**：存放达到最大重试次数仍失败的消息的队列
- **灰度规则（GrayRule）**：控制消息下发范围的规则，用于渐进式发布

## 需求

### 需求 1：即时消息下发

**用户故事：** 作为调用方，我希望能够即时下发消息到指定渠道，以便及时通知目标用户。

#### 验收标准

1. WHEN 调用方提交一个包含 bizType、bizId、channel、templateCode、receiver 和 variables 的合法下发请求 THEN 接入服务 SHALL 校验请求参数并将消息提交到消息中心处理
2. WHEN 消息中心接收到合法消息 THEN 消息中心 SHALL 生成全局唯一的 msgId（雪花算法）并返回给调用方
3. WHEN 消息中心处理新消息 THEN 消息中心 SHALL 将消息记录持久化到数据库，初始状态设为 PENDING
4. WHEN 消息记录持久化成功 THEN 消息中心 SHALL 通过 RocketMQ 事务消息投递到消息队列，并将状态更新为 SENDING
5. WHEN 渠道执行器从 MQ 消费到消息 THEN 渠道执行器 SHALL 调用对应渠道发送器执行下发
6. WHEN 渠道下发成功 THEN 渠道执行器 SHALL 将消息状态更新为 SUCCESS 并记录实际发送时间

### 需求 2：延迟与定时消息下发

**用户故事：** 作为调用方，我希望能够指定未来某个时间点下发消息，以便支持定时通知和延迟推送场景。

#### 验收标准

1. WHEN 调用方提交包含 sendTime 的延迟下发请求 THEN 接入服务 SHALL 校验 sendTime 合法性并将消息提交到消息中心
2. WHEN 延迟时间较短（适用 RocketMQ 延迟级别） THEN 消息中心 SHALL 通过 RocketMQ 延迟消息机制投递
3. WHEN 延迟时间较长或需要精确定时 THEN 消息中心 SHALL 将消息持久化到数据库，由 XXL-Job 定时任务扫描到期消息并投递到 MQ
4. WHEN 定时任务扫描到已到期的 PENDING 状态消息 THEN 定时任务 SHALL 将消息投递到 MQ 主队列进行消费

### 需求 3：消息幂等保障

**用户故事：** 作为调用方，我希望重复提交相同业务消息时系统能自动去重，以便避免用户收到重复消息。

#### 验收标准

1. WHEN 消息中心接收到消息 THEN 消息中心 SHALL 使用 bizType + bizId + channel 组合作为幂等键进行 Redis 分布式锁检查
2. WHEN Redis 幂等检查发现已存在相同幂等键 THEN 消息中心 SHALL 返回已有消息的 msgId，不产生新的消息记录
3. WHEN Redis 幂等检查通过 THEN 消息中心 SHALL 通过数据库唯一索引（uk_biz_channel）进行二次幂等校验
4. IF 数据库插入时发生唯一索引冲突（DuplicateKeyException） THEN 消息中心 SHALL 查询已有记录并返回幂等结果
5. WHEN 渠道执行器消费 MQ 消息 THEN 渠道执行器 SHALL 校验消息当前状态，对已处于终态（SUCCESS/CANCELLED/DEAD_LETTER）的消息跳过处理

### 需求 4：消息状态管理

**用户故事：** 作为调用方，我希望能够查询消息的实时状态，以便追踪消息下发进度。

#### 验收标准

1. THE 状态机 SHALL 仅允许以下合法状态转换：PENDING→SENDING、PENDING→CANCELLED、SENDING→SUCCESS、SENDING→FAILED、SENDING→CANCELLED、FAILED→RETRYING、FAILED→DEAD_LETTER、RETRYING→SENDING
2. WHEN 调用方通过 msgId 或 bizType+bizId 查询消息状态 THEN 接入服务 SHALL 返回消息的当前状态、重试次数和实际发送时间
3. WHEN 状态流转请求的 fromStatus 与数据库中当前状态不匹配 THEN 状态机 SHALL 拒绝该转换并返回失败结果
4. THE 状态机 SHALL 使用乐观锁（WHERE status = fromStatus）保证并发状态更新的安全性

### 需求 5：消息撤回

**用户故事：** 作为调用方，我希望能够撤回尚未成功下发的消息，以便在业务变更时及时取消通知。

#### 验收标准

1. WHEN 调用方提交撤回请求 THEN 接入服务 SHALL 校验目标消息是否处于可撤回状态（PENDING 或 SENDING）
2. WHEN 目标消息处于 PENDING 或 SENDING 状态 THEN 状态机 SHALL 将消息状态转换为 CANCELLED
3. WHEN 目标消息已处于终态（SUCCESS/DEAD_LETTER/CANCELLED） THEN 接入服务 SHALL 拒绝撤回请求并返回失败原因

### 需求 6：消息路由与灰度管控

**用户故事：** 作为运营人员，我希望能够通过黑白名单和灰度规则控制消息下发范围，以便实现精细化的消息管控。

#### 验收标准

1. WHEN 消息路由器处理消息 THEN 消息路由器 SHALL 首先检查接收人是否在黑名单中
2. WHEN 接收人命中黑名单 THEN 消息路由器 SHALL 拦截该消息并返回拦截原因，该消息不投递到 MQ
3. WHILE 白名单模式已启用 WHEN 接收人不在白名单中 THEN 消息路由器 SHALL 拦截该消息
4. WHILE 灰度规则已启用 WHEN 消息未命中灰度规则 THEN 消息路由器 SHALL 拦截该消息
5. WHEN 路由检查全部通过 THEN 消息路由器 SHALL 按优先级和权重从可用渠道配置中选择目标渠道
6. IF 指定渠道类型无可用渠道配置 THEN 消息路由器 SHALL 拦截该消息并返回"无可用渠道配置"

### 需求 7：消息模板渲染

**用户故事：** 作为调用方，我希望通过模板编码和变量参数生成消息内容，以便统一管理消息格式。

#### 验收标准

1. WHEN 消息中心接收到包含 templateCode 和 variables 的消息 THEN 消息中心 SHALL 使用 Freemarker 引擎渲染模板生成最终内容
2. WHEN 模板渲染完成 THEN 消息中心 SHALL 确保渲染结果中不包含未替换的占位符 `${...}`
3. IF 模板编码对应的模板不存在或未启用 THEN 消息中心 SHALL 抛出 TemplateRenderException 并将消息状态置为 FAILED（不进入重试）
4. IF 模板所需变量缺失 THEN 消息中心 SHALL 抛出 TemplateRenderException 并记录详细错误信息
5. WHEN 模板渲染时 THEN 消息中心 SHALL 优先从 Redis 缓存读取模板内容，缓存未命中时从数据库加载并写入缓存

### 需求 8：失败重试与死信处理

**用户故事：** 作为系统管理员，我希望下发失败的消息能够自动重试，并在重试耗尽后进入死信队列，以便保证消息不丢失。

#### 验收标准

1. WHEN 渠道下发失败且当前重试次数小于最大重试次数 THEN 重试处理器 SHALL 将消息投递到重试队列，并将 retryTimes 加 1
2. WHEN 重试处理器计算重试延迟 THEN 重试处理器 SHALL 按递增退避策略计算延迟时间（10s→30s→1min→5min→30min→1h→2h→6h→12h）
3. THE 重试处理器 SHALL 保证重试延迟序列严格单调递增，即第 n+1 次重试的延迟大于第 n 次
4. WHEN 渠道下发失败且当前重试次数已达到最大重试次数 THEN 重试处理器 SHALL 将消息投递到死信队列并将状态更新为 DEAD_LETTER
5. THE 系统 SHALL 保证任意消息的 retryTimes 不超过 maxRetryTimes

### 需求 9：多渠道统一抽象

**用户故事：** 作为开发者，我希望通过统一接口扩展新的消息渠道，以便快速接入短信、邮件、AppPush、WebHook 等多种下发渠道。

#### 验收标准

1. THE 渠道执行器 SHALL 通过 ChannelSender 接口统一抽象所有渠道的下发行为，每个实现需提供 getChannelType()、send() 和 healthCheck() 方法
2. WHEN 渠道执行器消费到消息 THEN 渠道执行器 SHALL 根据消息的 channel 字段自动路由到对应的渠道发送器实现
3. WHEN 新的渠道发送器实现注册到系统 THEN 渠道执行器 SHALL 自动识别并纳入路由范围
4. IF 消息指定的渠道类型无对应的渠道发送器 THEN 渠道执行器 SHALL 将消息投递到死信队列并记录错误原因

### 需求 10：渠道回执处理

**用户故事：** 作为系统管理员，我希望能够接收渠道服务商的回执回调，以便获取消息的最终投递状态。

#### 验收标准

1. WHEN 渠道服务商发送回执回调 THEN 接入服务 SHALL 验证回执签名的合法性
2. WHEN 回执签名验证通过 THEN 消息中心 SHALL 将回执数据插入回执记录表（t_msg_receipt）并更新消息最终状态
3. IF 回执签名验证失败 THEN 接入服务 SHALL 拒绝该回执请求

### 需求 11：接口鉴权与限流

**用户故事：** 作为系统管理员，我希望对调用方进行鉴权和限流，以便保护系统安全和稳定性。

#### 验收标准

1. WHEN 调用方发送请求 THEN API 网关 SHALL 使用 AppKey + Secret HMAC-SHA256 签名验证调用方身份
2. IF 鉴权失败 THEN API 网关 SHALL 拒绝请求并返回 401 错误
3. THE 接入服务 SHALL 通过 Sentinel 令牌桶算法对请求进行多维度限流（AppKey 级、接口级、IP 级）
4. WHEN 请求超过限流阈值 THEN 接入服务 SHALL 拒绝请求并返回 429 错误

### 需求 12：错误处理与降级

**用户故事：** 作为系统管理员，我希望系统在组件故障时能够自动降级和恢复，以便保证系统的高可用性。

#### 验收标准

1. IF 渠道 API 调用超过 5 秒未响应 THEN 渠道执行器 SHALL 捕获超时异常并将消息标记为下发失败，进入重试流程
2. IF RocketMQ 事务消息投递失败 THEN 消息中心 SHALL 保持消息状态为 PENDING 并返回客户端失败结果
3. IF Redis 集群不可用 THEN 消息中心 SHALL 降级到数据库唯一索引保障幂等，跳过 Redis 缓存直接查询数据库
4. IF 模板渲染失败（模板不存在、变量缺失、语法错误） THEN 消息中心 SHALL 将消息状态直接置为 FAILED 且不进入重试流程
5. IF 渠道连续超时 THEN 渠道执行器 SHALL 通过 Sentinel 熔断该渠道的调用

### 需求 13：数据安全与脱敏

**用户故事：** 作为安全管理员，我希望系统对敏感数据进行加密存储和脱敏输出，以便满足数据安全合规要求。

#### 验收标准

1. THE 系统 SHALL 对渠道配置中的 Secret 和 Token 使用 AES-256 加密存储，运行时解密使用
2. THE 系统 SHALL 在日志输出中对手机号、邮箱等敏感信息进行脱敏处理
3. THE 系统 SHALL 全链路使用 HTTPS/TLS 传输，RocketMQ 开启 ACL 访问控制
4. THE 系统 SHALL 使用 MyBatis 参数化查询，禁止 SQL 拼接以防止 SQL 注入
