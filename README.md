# MQ Delivery System

基于 RabbitMQ 的高可用消息队列投递系统，提供可靠的消息投递、回执跟踪和自动重试机制。

## 系统概述

MQ Delivery System 是一个消息队列包装器，连接上游系统（通过 Dubbo RPC）与下游 HTTP 端点，确保消息持久化、自动重试和全面的监控能力。

### 核心功能

- **RPC 消息接入** — 通过 Dubbo RPC 接收上游系统消息
- **消息持久化** — 基于 RabbitMQ 的持久化队列和消息存储
- **HTTP 投递** — 向下游渠道发送 HTTP POST 请求
- **回执处理** — 处理并转发回执到上游系统
- **自动重试** — 指数退避重试机制（1s → 300s）
- **死信队列** — 失败消息的死信队列管理
- **管理后台** — 消息查询、重发和监控界面
- **分布式锁** — 基于 Redis 的分布式协调机制

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 1.8 | 运行环境 |
| Spring Boot | 2.7.18 | 应用框架 |
| Spring Cloud | 2021.0.8 | 微服务治理 |
| RabbitMQ | 3.x+ | 消息队列 |
| MySQL | 8.0 | 持久化存储 |
| Redis | 6.x+ | 分布式锁 |
| MyBatis-Plus | 3.5.5 | ORM 框架 |
| Dubbo | 3.2.9 | RPC 框架 |
| Nacos | 2.2.4 | 注册中心 |
| jqwik | 1.8.2 | 属性测试框架 |
| Testcontainers | 1.19.3 | 集成测试 |

## 项目结构

```
message-delivery-system/
├── mq-delivery/                    # MQ 投递服务
│   ├── src/main/java/
│   │   └── com/msg/delivery/
│   │       ├── client/            # HTTP 投递客户端
│   │       ├── config/            # 配置类
│   │       ├── consumer/          # RabbitMQ 消费者
│   │       ├── controller/        # REST 控制器
│   │       ├── dto/               # 数据传输对象
│   │       ├── entity/            # 数据库实体
│   │       ├── exception/         # 异常定义
│   │       ├── lock/              # 分布式锁管理
│   │       ├── mapper/            # MyBatis Mapper
│   │       ├── processor/         # 回执处理器
│   │       ├── publisher/         # RabbitMQ 发布者
│   │       ├── scheduler/         # 重试调度器
│   │       ├── service/           # 业务服务层
│   │       └── validator/         # 消息验证器
│   └── src/test/java/             # 单元测试和属性测试
└── pom.xml                         # 父 POM
```

## 消息状态流转

```
PENDING → DELIVERED (成功投递)
    ↓
FAILED → RETRYING → DELIVERED (重试成功)
    ↓
DEAD_LETTER (超过最大重试次数)
```

## 服务接口（Dubbo RPC）

业务方通过 Dubbo 接口调用消息投递服务：

```java
@DubboReference(version = "1.0.0")
private MessageRpcService messageRpcService;

// 提交消息
MessageSubmitRequest request = MessageSubmitRequest.builder()
    .messageId("msg-" + UUID.randomUUID())
    .destinationUrl("https://downstream.example.com/callback")
    .payload("{\"data\":\"example\"}")
    .build();

MessageSubmitResponse response = messageRpcService.submitMessage(request);
```

### 接口方法

| 方法 | 说明 | 超时时间 |
|------|------|----------|
| `submitMessage(MessageSubmitRequest)` | 提交消息进行投递 | 3000ms |

## 环境依赖

启动前需确保以下服务可用：

- **MySQL 8.0** — 执行 `mq-delivery/src/main/resources/db/schema.sql` 初始化表结构
- **Redis 6.x+** — 用于分布式锁
- **RabbitMQ 3.x+** — 消息队列服务
- **Nacos 2.x** — 服务注册与发现

## 快速开始

### 1. 初始化数据库

```bash
mysql -u root -p < mq-delivery/src/main/resources/db/schema.sql
```

### 2. 配置文件

修改 `mq-delivery/src/main/resources/application.yml`：

```yaml
# 数据源配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mq_delivery?useUnicode=true&characterEncoding=utf8
    username: root
    password: your_password
    
  # Redis 配置
  redis:
    host: localhost
    port: 6379
    
  # RabbitMQ 配置
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

# Dubbo 配置
dubbo:
  application:
    name: mq-delivery-service
  protocol:
    name: dubbo
    port: 20880
  registry:
    address: nacos://localhost:8848
```

### 3. 编译项目

```bash
mvn clean compile
```

### 4. 运行测试

```bash
mvn test
```

测试使用 H2 内存数据库和 Testcontainers，自动启动 RabbitMQ 和 MySQL 容器。

### 5. 启动服务

```bash
cd mq-delivery
mvn spring-boot:run
```

或打包后运行：

```bash
mvn clean package -DskipTests
java -jar mq-delivery/target/mq-delivery-1.0.0-SNAPSHOT.jar
```

## 核心组件

### 1. MessageRpcService — RPC 接口

接收上游系统消息，验证负载，持久化到 MySQL，发布到 RabbitMQ。

### 2. MessageValidator — 消息验证

验证规则：
- 负载不为空
- 负载大小 ≤ 1MB
- 目标 URL 为有效的 HTTP/HTTPS 地址
- 必填字段完整

### 3. RabbitMQPublisher — 消息发布

发布消息到 RabbitMQ，配置：
- 持久化队列（durable）
- 持久化消息（persistent）
- 发布确认（publisher confirms）

### 4. HttpDeliveryClient — HTTP 投递

向下游渠道发送 HTTP POST 请求：
- 连接超时：5 秒
- 读取超时：30 秒
- 请求头：`X-Message-Id`、`X-Timestamp`
- Content-Type: `application/json`

### 5. DistributedLockManager — 分布式锁

基于 Redis 的分布式锁：
- 锁超时：60 秒（防止死锁）
- 使用 `SET NX EX` 命令
- Lua 脚本保证原子性释放

### 6. RetryScheduler — 重试调度

指数退避重试策略：

| 重试次数 | 1 | 2 | 3 | 4 | 5 |
|---------|---|---|---|---|---|
| 延迟时间 | 1s | 2s | 4s | 8s | 16s |

- 最大延迟：300 秒
- 最大重试次数：5 次
- 超过最大次数后移入死信队列

### 7. ReceiptProcessor — 回执处理

从下游响应中提取回执：
- 解析 JSON 响应体
- 提取 `receipt` 字段
- 发布到回执队列
- 更新消息状态为 DELIVERED

## 数据库表结构

### t_mq_message — 消息主表

| 字段 | 类型 | 说明 |
|------|------|------|
| message_id | VARCHAR(64) | 消息唯一标识（主键）|
| destination_url | VARCHAR(2048) | 下游 HTTP 端点 |
| payload | TEXT | 消息负载（JSON）|
| status | VARCHAR(32) | 消息状态 |
| retry_count | INT | 当前重试次数 |
| max_retries | INT | 最大重试次数 |
| failure_reason | VARCHAR(1024) | 失败原因 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |
| delivery_time | DATETIME | 投递完成时间 |

### t_mq_delivery_attempt — 投递尝试记录

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 自增主键 |
| message_id | VARCHAR(64) | 关联消息 ID |
| attempt_number | INT | 尝试次数 |
| http_status | INT | HTTP 状态码 |
| response_body | TEXT | 响应体 |
| delivery_result | VARCHAR(32) | 投递结果 |
| error_message | VARCHAR(1024) | 错误信息 |
| attempt_time | DATETIME | 尝试时间 |
| latency_ms | BIGINT | 延迟（毫秒）|

### t_mq_receipt — 回执记录

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 自增主键 |
| message_id | VARCHAR(64) | 关联消息 ID |
| receipt_data | TEXT | 回执数据（JSON）|
| receipt_time | DATETIME | 回执时间 |

## RabbitMQ 队列配置

### 主队列（Main Queue）

- 队列名：`mq.delivery.main`
- 持久化：是
- 自动删除：否
- 消费模式：手动确认（MANUAL）

### 回执队列（Receipt Queue）

- 队列名：`mq.delivery.receipt`
- 持久化：是
- 自动删除：否

### 死信队列（Dead Letter Queue）

- 队列名：`mq.delivery.dlq`
- 持久化：是
- 自动删除：否

## 测试

项目使用 jqwik 进行属性测试，确保系统正确性：

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=HttpDeliveryClientTest

# 运行属性测试
mvn test -Dtest=HttpDeliveryClientProperties
```

### 测试覆盖

- **单元测试** — 使用 Mockito 模拟依赖
- **属性测试** — 使用 jqwik 验证正确性属性
- **集成测试** — 使用 Testcontainers 启动真实服务

## 监控与运维

### 健康检查

```bash
curl http://localhost:8080/actuator/health
```

检查项：
- RabbitMQ 连接状态
- MySQL 连接状态
- Redis 连接状态

### 消息查询

通过管理接口查询消息状态：

```java
// 按消息 ID 查询
Optional<MessageDetail> detail = messageQueryService.queryByMessageId(messageId);

// 按状态查询
Page<MessageSummary> messages = messageQueryService.queryByStatus(
    MessageStatus.FAILED, 
    PageRequest.of(0, 100)
);

// 按时间范围查询
Page<MessageSummary> messages = messageQueryService.queryByTimeRange(
    startTime, 
    endTime, 
    PageRequest.of(0, 100)
);
```

### 消息重发

```java
// 单条重发
messageResendService.resendMessage(messageId);

// 批量重发（最多 100 条）
BatchResendResult result = messageResendService.batchResend(messageIds);
```

## 性能指标

- **RPC 响应时间** — < 100ms（消息接收）
- **HTTP 投递超时** — 连接 5s，读取 30s
- **查询响应时间** — < 2s（最多 1000 条记录）
- **健康检查响应** — < 1s
- **优雅关闭等待** — 最多 60s

## 故障处理

### Redis 不可用

- 跳过分布式锁获取
- 消息可能被多次处理（下游需实现幂等）

### RabbitMQ 不可用

- 消息提交失败，返回错误给上游
- 上游可安全重试

### MySQL 不可用

- 消息提交失败，返回错误给上游
- 已在队列中的消息继续处理

### 下游超时

- 自动重试，指数退避
- 超过最大次数后移入死信队列

## 开发指南

### 添加新的投递渠道

1. 实现 `DeliveryChannel` 接口
2. 注册到 `ChannelRegistry`
3. 配置路由规则

### 自定义重试策略

修改 `RetryScheduler` 中的退避算法：

```java
private long calculateDelay(int attemptNumber) {
    // 自定义延迟计算逻辑
    return Math.min(
        initialDelay * (long) Math.pow(2, attemptNumber - 1),
        maxDelay
    );
}
```

### 扩展消息验证

在 `MessageValidator` 中添加验证规则：

```java
public void validate(MessageSubmitRequest request) {
    // 添加自定义验证逻辑
    if (!customValidation(request)) {
        throw new ValidationException("CUSTOM_VALIDATION_FAILED");
    }
}
```

## 常见问题

### Q: 消息会丢失吗？

A: 不会。消息在接收时持久化到 MySQL 和 RabbitMQ，使用手动确认模式，只有在成功投递或移入死信队列后才会确认。

### Q: 如何保证消息不重复投递？

A: 系统使用分布式锁防止并发处理。下游系统应根据 `X-Message-Id` 实现幂等处理。

### Q: 死信队列中的消息如何处理？

A: 可通过管理接口查询死信消息，修复下游问题后手动重发。

### Q: 如何提高吞吐量？

A: 调整 RabbitMQ 消费者并发数和预取数量，增加消费者实例。

## 许可证

本项目采用 MIT 许可证。

## 联系方式

如有问题或建议，请提交 Issue 或 Pull Request。
