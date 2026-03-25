# 消息下发系统（Message Delivery System）

面向生产环境的高可靠、高并发消息分发平台，统一承接通知、告警、运营推送、第三方回调等多种消息下发场景。

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 11 | 运行环境 |
| SpringBoot | 2.7.18 | 应用框架 |
| SpringCloud | 2021.0.8 | 微服务治理 |
| RocketMQ | 4.9.7 | 消息队列 |
| MySQL | 8.0 | 持久化存储 |
| Redis | 6.x+ | 缓存 & 分布式锁 |
| MyBatis-Plus | 3.5.5 | ORM |
| Freemarker | 2.3.32 | 模板引擎 |
| Dubbo | 3.2.9 | RPC 框架 |
| Nacos | 2.2.4 | 注册中心 |
| Sentinel | 1.8.7 | 限流熔断 |
| Hutool | 5.8.24 | 雪花ID生成 |
| jqwik | 1.8.2 | 属性测试 |

## 项目结构

```
message-delivery-system/
├── msg-common      # 公共模块：枚举、DTO、实体、Mapper、工具类
├── msg-access      # 接入服务（端口 8081）：API 网关、鉴权、限流、Controller
├── msg-center      # 消息中心（端口 8082）：幂等、路由、模板渲染、状态机、核心流程
├── msg-executor    # 渠道执行器（端口 8083）：MQ 消费、多渠道下发、熔断
└── pom.xml         # 父 POM
```

## 核心功能

- **即时消息下发** — 幂等检查 → 模板渲染 → 消息路由 → 持久化 → RocketMQ 投递
- **延迟/定时下发** — 短延迟走 RocketMQ 延迟消息，长延迟走 DB + 定时任务扫描
- **消息撤回** — PENDING/SENDING 状态可撤回，终态拒绝
- **状态查询** — 按 msgId 或 bizType+bizId 查询
- **多渠道抽象** — SMS / EMAIL / APP_PUSH / WEBHOOK，策略模式自动路由
- **三级幂等保障** — Redis 分布式锁 → DB 唯一索引 → 消费端终态校验
- **重试与死信** — 递增退避（10s→12h），最大重试后进入死信队列
- **渠道回执** — HMAC 签名验证 → 插入回执记录 → 更新消息终态
- **灰度管控** — 黑名单 / 白名单 / 灰度规则，路由层统一拦截
- **鉴权限流** — HMAC-SHA256 签名验证 + Sentinel 多维度限流（AppKey/接口/IP）
- **数据安全** — AES-256 加密存储、日志脱敏、参数化查询防注入

## 消息状态机

```
PENDING → SENDING → SUCCESS
    ↓         ↓
CANCELLED  FAILED → RETRYING → SENDING
               ↓
          DEAD_LETTER
```

合法转换：PENDING→SENDING、PENDING→CANCELLED、SENDING→SUCCESS、SENDING→FAILED、SENDING→CANCELLED、FAILED→RETRYING、FAILED→DEAD_LETTER、RETRYING→SENDING

## 服务接口（Dubbo RPC）

业务方通过 msg-common 依赖引入 `MessageRpcService` 接口，使用 `@DubboReference` 注入调用：

```java
@DubboReference(version = "1.0.0")
private MessageRpcService messageRpcService;

// 即时下发
SendResult result = messageRpcService.sendNow(request);

// 延迟下发
SendResult result = messageRpcService.sendDelay(delayRequest);

// 状态查询
MessageStatusVO status = messageRpcService.queryStatus(query);

// 消息撤回
CancelResult result = messageRpcService.cancel(cancelRequest);

// 渠道回执
messageRpcService.handleReceipt(receiptCallback);
```

| 方法 | 说明 |
|------|------|
| `sendNow(SendRequest)` | 即时消息下发 |
| `sendDelay(DelaySendRequest)` | 延迟/定时消息下发 |
| `queryStatus(StatusQuery)` | 消息状态查询 |
| `cancel(CancelRequest)` | 消息撤回 |
| `handleReceipt(ReceiptCallback)` | 渠道回执回调 |

注册中心：Nacos（默认 `localhost:8848`）
协议：Dubbo（默认端口 `20880`）

## 环境依赖

启动前需确保以下服务可用：

- MySQL 8.0 — 执行 `msg-common/src/main/resources/sql/schema.sql` 初始化表结构
- Redis 6.x+
- RocketMQ 4.9.x（NameServer + Broker）
- Nacos 2.x（注册中心，Dubbo 服务注册发现）

## 快速开始

```bash
# 1. 初始化数据库
mysql -u root -p < msg-common/src/main/resources/sql/schema.sql

# 2. 修改各模块 application.yml 中的数据源、Redis、RocketMQ 连接信息

# 3. 编译
mvn clean package -DskipTests

# 4. 依次启动
java -jar msg-center/target/msg-center-1.0.0-SNAPSHOT.jar
java -jar msg-access/target/msg-access-1.0.0-SNAPSHOT.jar
java -jar msg-executor/target/msg-executor-1.0.0-SNAPSHOT.jar
```

## 配置说明

各模块 `application.yml` 中的关键配置项：

```yaml
# 数据源
spring.datasource.url: jdbc:mysql://localhost:3306/msg_db
spring.datasource.username: root
spring.datasource.password: your_password

# Redis
spring.redis.host: localhost
spring.redis.port: 6379

# RocketMQ
rocketmq.name-server: localhost:9876

# Dubbo RPC
dubbo.protocol.name: dubbo
dubbo.protocol.port: 20880
dubbo.registry.address: nacos://localhost:8848

# 定时任务
msg.job.scan-interval: 60000   # 扫描间隔（毫秒）
msg.job.batch-size: 100        # 每批扫描数量

# 回执签名密钥
msg.receipt.secret: your-receipt-secret
```

## 运行测试

```bash
mvn clean test
```

测试使用 H2 内存数据库和 Mockito，无需外部依赖。测试 DDL 位于 `msg-common/src/test/resources/sql/schema-h2.sql`。

## 数据库表

| 表名 | 说明 |
|------|------|
| `t_msg` | 消息主表，唯一索引 `uk_biz_channel(biz_type, biz_id, channel)` |
| `t_msg_receipt` | 渠道回执记录表 |
| `t_channel_config` | 渠道配置表（含加密的 Secret/Token） |
| `t_msg_template` | 消息模板表（Freemarker 语法） |

## 重试策略

递增退避，共 9 级：

| 次数 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
|------|---|---|---|---|---|---|---|---|---|
| 延迟 | 10s | 30s | 1min | 5min | 30min | 1h | 2h | 6h | 12h |

超过最大重试次数后进入死信队列（DEAD_LETTER）。

## 降级策略

- **Redis 不可用** → 自动降级到 DB 唯一索引保障幂等，跳过缓存直接查 DB
- **RocketMQ 投递失败** → 消息保持 PENDING 状态，返回客户端失败，可安全重试
- **模板渲染失败** → 消息直接置为 FAILED，不进入重试
- **渠道连续超时** → Sentinel 熔断该渠道（慢调用比例 > 50% 触发，持续 30s）
