# 需求文档

## 简介

msg-admin 是消息下发系统的后台管理模块，为前端可视化管理页面提供 REST API 接口。该模块覆盖消息模板管理、渠道配置管理、消息记录查询与统计、黑白名单管理等核心运营功能，使运营和管理人员能够通过 Web 界面对消息下发系统进行配置、监控和运维操作。

模块作为独立的 SpringBoot 微服务部署，依赖 msg-common 公共模块，直接操作 MySQL 数据库和 Redis 缓存，不经过 msg-access 接入层。

## 术语表

- **管理服务（AdminService）**：msg-admin 模块的后端服务，为前端管理页面提供 REST API
- **消息模板（MessageTemplate）**：基于 Freemarker 语法的消息内容模板，对应 t_msg_template 表
- **渠道配置（ChannelConfig）**：消息下发渠道的连接参数和策略配置，对应 t_channel_config 表
- **消息记录（MessageRecord）**：已下发或待下发的消息数据，对应 t_msg 表
- **消息回执（MessageReceipt）**：渠道服务商返回的投递状态回执，对应 t_msg_receipt 表
- **黑名单（Blacklist）**：禁止接收消息的接收人列表
- **白名单（Whitelist）**：白名单模式下允许接收消息的接收人列表
- **分页查询（PageQuery）**：基于 MyBatis-Plus 的分页查询机制，包含 pageNum、pageSize 参数
- **操作员（Operator）**：使用后台管理平台的运营或管理人员

## 需求

### 需求 1：消息模板管理

**用户故事：** 作为操作员，我希望能够通过管理页面对消息模板进行增删改查操作，以便统一管理各渠道的消息内容格式。

#### 验收标准

1. WHEN 操作员提交包含 templateCode、templateName、channelType、content 和 variables 的创建请求 THEN 管理服务 SHALL 校验参数合法性并将模板记录插入 t_msg_template 表
2. WHEN 操作员提交的 templateCode 与已有模板重复 THEN 管理服务 SHALL 拒绝创建并返回"模板编码已存在"错误信息
3. WHEN 操作员提交模板更新请求 THEN 管理服务 SHALL 更新对应模板记录并清除该模板的 Redis 缓存
4. WHEN 操作员提交模板删除请求 THEN 管理服务 SHALL 删除对应模板记录并清除该模板的 Redis 缓存
5. WHEN 操作员提交分页查询请求（支持按 templateName、channelType、enabled 筛选） THEN 管理服务 SHALL 返回符合条件的模板分页列表
6. WHEN 操作员查询单个模板详情 THEN 管理服务 SHALL 返回该模板的完整信息（含 content 和 variables）
7. WHEN 操作员切换模板的启用/禁用状态 THEN 管理服务 SHALL 更新 enabled 字段并清除该模板的 Redis 缓存

### 需求 2：渠道配置管理

**用户故事：** 作为操作员，我希望能够通过管理页面对渠道配置进行增删改查操作，以便灵活管理各消息下发渠道的连接参数和策略。

#### 验收标准

1. WHEN 操作员提交包含 channelCode、channelType、channelName、config、weight、qpsLimit、priority 的创建请求 THEN 管理服务 SHALL 校验参数合法性并将渠道配置插入 t_channel_config 表
2. WHEN 操作员提交的 channelCode 与已有配置重复 THEN 管理服务 SHALL 拒绝创建并返回"渠道编码已存在"错误信息
3. WHEN 操作员提交渠道配置更新请求 THEN 管理服务 SHALL 更新对应配置记录
4. WHEN 操作员提交渠道配置删除请求 THEN 管理服务 SHALL 删除对应配置记录
5. WHEN 操作员提交分页查询请求（支持按 channelType、enabled 筛选） THEN 管理服务 SHALL 返回符合条件的渠道配置分页列表
6. WHEN 操作员切换渠道配置的启用/禁用状态 THEN 管理服务 SHALL 更新 enabled 字段
7. THE 管理服务 SHALL 对渠道配置中的 Secret 和 Token 字段在 API 响应中进行脱敏处理（仅显示前4位和后4位，中间用星号替代）

### 需求 3：消息记录查询

**用户故事：** 作为操作员，我希望能够查询和检索消息下发记录，以便监控消息下发状态和排查问题。

#### 验收标准

1. WHEN 操作员提交分页查询请求（支持按 msgId、bizType、bizId、channel、status、receiver、时间范围筛选） THEN 管理服务 SHALL 返回符合条件的消息记录分页列表
2. WHEN 操作员查询单条消息详情 THEN 管理服务 SHALL 返回该消息的完整信息（含回执记录列表）
3. WHEN 操作员按 msgId 查询消息 THEN 管理服务 SHALL 返回精确匹配的消息记录
4. THE 管理服务 SHALL 对消息记录中的 receiver 字段在 API 响应中进行脱敏处理（手机号中间4位、邮箱用户名部分用星号替代）
5. WHEN 操作员查询消息的回执记录 THEN 管理服务 SHALL 返回该消息关联的所有回执记录列表

### 需求 4：消息统计概览

**用户故事：** 作为操作员，我希望能够查看消息下发的统计数据，以便了解系统运行状况和各渠道的下发效果。

#### 验收标准

1. WHEN 操作员请求统计概览 THEN 管理服务 SHALL 返回指定时间范围内各状态（SUCCESS、FAILED、DEAD_LETTER、PENDING、SENDING）的消息数量
2. WHEN 操作员请求渠道维度统计 THEN 管理服务 SHALL 返回指定时间范围内各渠道（SMS、EMAIL、APP_PUSH、WEBHOOK）的下发总量和成功率
3. WHEN 操作员请求趋势数据 THEN 管理服务 SHALL 返回指定时间范围内按天聚合的消息下发量趋势数据

### 需求 5：黑白名单管理

**用户故事：** 作为操作员，我希望能够管理消息接收人的黑白名单，以便控制消息下发范围。

#### 验收标准

1. WHEN 操作员提交包含 receiver、channel 和 reason 的黑名单添加请求 THEN 管理服务 SHALL 将该接收人添加到黑名单并同步更新 Redis 缓存
2. WHEN 操作员提交黑名单移除请求 THEN 管理服务 SHALL 移除该接收人的黑名单记录并同步更新 Redis 缓存
3. WHEN 操作员提交分页查询请求（支持按 receiver、channel 筛选） THEN 管理服务 SHALL 返回符合条件的黑名单分页列表
4. WHEN 操作员提交包含 receiver 和 channel 的白名单添加请求 THEN 管理服务 SHALL 将该接收人添加到白名单并同步更新 Redis 缓存
5. WHEN 操作员提交白名单移除请求 THEN 管理服务 SHALL 移除该接收人的白名单记录并同步更新 Redis 缓存
6. WHEN 操作员提交分页查询请求 THEN 管理服务 SHALL 返回符合条件的白名单分页列表
7. THE 管理服务 SHALL 对黑白名单中的 receiver 字段在 API 响应中进行脱敏处理

### 需求 6：REST API 规范

**用户故事：** 作为前端开发者，我希望后台管理 API 遵循统一的 RESTful 规范，以便高效对接前端页面。

#### 验收标准

1. THE 管理服务 SHALL 使用统一的响应包装格式 Result<T>（包含 code、message、data、success 字段）
2. THE 管理服务 SHALL 对所有 API 路径使用 /admin 前缀（如 /admin/template、/admin/channel、/admin/message）
3. WHEN 请求参数校验失败 THEN 管理服务 SHALL 返回 HTTP 400 状态码和具体的校验错误信息
4. WHEN 请求的资源不存在 THEN 管理服务 SHALL 返回 HTTP 404 状态码和"资源不存在"错误信息
5. IF 服务内部发生未预期异常 THEN 管理服务 SHALL 返回 HTTP 500 状态码和通用错误信息，不暴露内部堆栈
6. THE 管理服务 SHALL 支持跨域请求（CORS），允许前端管理页面从不同域名访问 API
7. THE 管理服务 SHALL 对所有分页查询接口返回统一的分页结构（包含 records、total、pageNum、pageSize 字段）

### 需求 7：管理端鉴权

**用户故事：** 作为安全管理员，我希望后台管理 API 具备独立的鉴权机制，以便防止未授权访问管理功能。

#### 验收标准

1. WHEN 操作员登录管理平台 THEN 管理服务 SHALL 验证操作员的用户名和密码，验证通过后返回 JWT Token
2. WHEN 操作员访问管理 API THEN 管理服务 SHALL 从请求头 Authorization 中提取 JWT Token 并验证有效性
3. IF JWT Token 缺失或验证失败 THEN 管理服务 SHALL 拒绝请求并返回 HTTP 401 状态码
4. IF JWT Token 已过期 THEN 管理服务 SHALL 拒绝请求并返回 HTTP 401 状态码和"Token 已过期"提示
5. THE 管理服务 SHALL 将 JWT Token 的有效期设置为可配置参数（默认 2 小时）

### 需求 8：操作日志记录

**用户故事：** 作为安全管理员，我希望后台管理的所有写操作都有日志记录，以便审计和追溯操作历史。

#### 验收标准

1. WHEN 操作员执行创建、更新、删除或状态变更操作 THEN 管理服务 SHALL 记录操作日志（包含操作员、操作类型、操作对象、操作时间、操作前后数据）
2. WHEN 操作员查询操作日志 THEN 管理服务 SHALL 返回符合条件的操作日志分页列表（支持按操作员、操作类型、时间范围筛选）
3. THE 管理服务 SHALL 将操作日志持久化到数据库表 t_admin_operation_log
