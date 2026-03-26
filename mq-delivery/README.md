# MQ Delivery System

基于RabbitMQ的消息队列投递系统，提供可靠消息投递、回执跟踪、自动重试、死信队列处理。

## 功能特性

- **可靠消息投递**: 基于RabbitMQ持久化队列和手动确认模式，确保消息不丢失
- **自动重试**: 支持指数退避重试策略，最多重试5次
- **回执处理**: 从下游HTTP响应中提取回执，转发给上游系统
- **死信队列**: 失败消息自动进入死信队列，支持手动重发
- **分布式锁**: 基于Redis的分布式锁，防止消息重复处理
- **监控统计**: 提供吞吐量、延迟、失败率等监控指标
- **优雅关闭**: 支持优雅关闭，确保处理中的消息不丢失

## 技术栈

- Spring Boot 2.7.18
- RabbitMQ (AMQP)
- MySQL + MyBatis-Plus
- Redis
- Dubbo 3.2.9
- WebFlux (HTTP Client)

## 快速开始

### 前置条件

- JDK 1.8+
- Maven 3.6+
- MySQL 5.7+
- Redis 5.0+
- RabbitMQ 3.8+

### 配置

编辑 `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mq_delivery
    username: root
    password: root
  redis:
    host: localhost
    port: 6379
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

### 数据库初始化

执行 `src/main/resources/db/schema.sql` 创建数据库表。

### 启动应用

```bash
mvn spring-boot:run
```

应用将在端口 8083 启动。

## 架构设计

### 核心组件

- **MessageRpcService**: Dubbo RPC接口，接收上游消息
- **MessageDeliveryConsumer**: RabbitMQ消费者，处理消息投递
- **HttpDeliveryClient**: HTTP客户端，投递消息到下游
- **RetryScheduler**: 重试调度器，实现指数退避重试
- **DistributedLockManager**: 分布式锁管理器，防止并发处理
- **ReceiptProcessor**: 回执处理器，提取并转发回执

### 消息流程

1. 上游系统通过Dubbo RPC提交消息
2. 消息持久化到MySQL和RabbitMQ
3. 消费者从队列获取消息并加锁
4. HTTP客户端投递消息到下游
5. 成功：提取回执，更新状态，确认消息
6. 失败：调度重试或移入死信队列

### 队列配置

- **主队列** (mq.delivery.main): 待投递消息
- **回执队列** (mq.delivery.receipt): 投递回执
- **死信队列** (mq.delivery.dlq): 失败消息

## API接口

### 查询接口

- `GET /api/messages/{messageId}` - 查询消息详情
- `GET /api/messages?status={status}` - 按状态查询
- `GET /api/messages?startTime={start}&endTime={end}` - 按时间范围查询

### 管理接口

- `POST /api/messages/{messageId}/resend` - 重发单条消息
- `POST /api/messages/batch-resend` - 批量重发消息

### 监控接口

- `GET /api/statistics/throughput?window={minutes}` - 吞吐量统计
- `GET /api/statistics/latency?window={minutes}` - 延迟统计
- `GET /api/statistics/failure-rate?window={minutes}` - 失败率统计
- `GET /api/statistics/queue-depths` - 队列深度

### 健康检查

- `GET /health` - 健康检查

## 配置参数

### 重试配置

```yaml
mq:
  retry:
    max-attempts: 5              # 最大重试次数
    initial-delay-seconds: 1     # 初始延迟（秒）
    max-delay-seconds: 300       # 最大延迟（秒）
```

### HTTP配置

```yaml
mq:
  http:
    connection-timeout-seconds: 5   # 连接超时
    read-timeout-seconds: 30        # 读取超时
```

### 锁配置

```yaml
mq:
  lock:
    timeout-seconds: 60  # 锁超时时间
```

## 监控指标

- 消息吞吐量 (messages/second)
- 平均投递延迟 (milliseconds)
- 失败率 (percentage)
- 队列深度 (message count)

## 故障处理

### RabbitMQ连接失败

- 系统返回错误给上游，不持久化消息
- 上游系统应重试提交

### MySQL连接失败

- 系统返回错误给上游，不发布到队列
- 上游系统应重试提交

### Redis连接失败

- 消费者跳过处理，消息重新入队
- 等待Redis恢复后自动重试

### 下游超时或5xx错误

- 自动重试，指数退避
- 5次失败后进入死信队列

### 下游4xx错误

- 视为永久失败，直接进入死信队列
- 需要人工介入修复

## 开发指南

### 添加新功能

1. 在 `com.msg.delivery` 包下创建相应的类
2. 遵循现有的分层架构（controller/service/mapper）
3. 编写单元测试和集成测试
4. 更新文档

### 运行测试

```bash
mvn test
```

### 构建部署

```bash
mvn clean package
java -jar target/mq-delivery-1.0.0-SNAPSHOT.jar
```

## 许可证

Copyright © 2024 Message Delivery System
