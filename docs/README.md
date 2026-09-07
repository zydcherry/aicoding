# API通知系统 - 项目文档

## 文档版本
- **版本号**: v2.0
- **更新日期**: 2026-09-06
- **状态**: 已实现订阅-发布模式

---

## 📋 项目概述

### 系统定位
API通知系统是一个**B2B业务系统集成平台**，专注于企业内部及外部系统之间的事件驱动通信。

**核心功能**：
- 📢 事件发布与订阅管理（发布-订阅模式）
- 🔄 异步消息推送
- 🎯 自动订阅者匹配
- 🔁 智能重试机制
- 📊 通知记录与追踪

**设计理念**：
- 事件驱动架构
- 系统解耦
- 高可靠性
- 易扩展

---

## 🏗️ 系统架构

### 架构模式

系统支持**两种推送模式**：

#### 1. 订阅-发布模式（Pub-Sub）✅ 已实现
```
业务系统 → 发布事件 → 通知系统
                        ↓
                   自动匹配订阅者
                        ↓
            ┌───────────┼───────────┐
            ↓           ↓           ↓
         CRM系统     物流系统     财务系统
```

**特点**：
- 业务系统**不指定接收方**
- 系统**自动匹配**订阅该事件类型的所有订阅者
- 一对多广播
- 松耦合

**使用场景**：
- 业务事件通知（订单创建、用户注册）
- 多系统需要同一事件
- 订阅关系动态变化

#### 2. 点对点推送模式（P2P）✅ 已实现
```
业务系统 → 指定目标 + 消息 → 通知系统 → 指定的第三方系统
```

**特点**：
- 业务系统**明确指定**接收方
- 一对一推送
- 直接通信

**使用场景**：
- 特定系统的API调用
- 需要精确控制接收方
- 点对点集成

### 技术架构

```
┌─────────────────────────────────────────────────────┐
│                   接入层（API）                      │
│  ┌──────────────┐  ┌──────────────┐                │
│  │ 订阅管理API  │  │ 事件发布API  │                │
│  └──────────────┘  └──────────────┘                │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│                   业务层（Service）                  │
│  ┌──────────────────┐  ┌──────────────────┐        │
│  │ SubscriptionService│  │   EventService   │       │
│  │ - 订阅CRUD        │  │ - 发布事件       │       │
│  │ - 事件类型索引    │  │ - 自动匹配       │       │
│  └──────────────────┘  └──────────────────┘        │
│                             ↓                        │
│                    ┌──────────────────┐             │
│                    │  EventPublisher  │             │
│                    │  - 异步推送      │             │
│                    └──────────────────┘             │
└─────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│                   推送层（Core）                     │
│  ┌──────────────────────────────────────────────┐  │
│  │            UnifiedPusher                      │  │
│  │  - 优先级队列                                │  │
│  │  - 工作线程池                                │  │
│  │  - 支持事件驱动和点对点两种模式             │  │
│  └──────────────────────────────────────────────┘  │
│                        ↓                            │
│  ┌──────────────────────────────────────────────┐  │
│  │         UnifiedHttpClient                     │  │
│  │  - 模板引擎                                  │  │
│  │  - HTTP执行                                  │  │
│  │  - 响应解析                                  │  │
│  └──────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
                        ↓
              第三方系统回调URL
```

---

## 💾 数据模型

### 核心表结构

#### 1. subscription - 订阅表
```sql
CREATE TABLE subscription (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    subscriber_name VARCHAR(128) NOT NULL COMMENT '订阅者名称',
    callback_url VARCHAR(512) NOT NULL COMMENT '回调URL',
    event_types JSON NOT NULL COMMENT '订阅的事件类型列表',
    secret_key VARCHAR(128) COMMENT '签名密钥',
    status TINYINT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    description VARCHAR(512) COMMENT '描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

#### 2. event - 事件表
```sql
CREATE TABLE event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) UNIQUE NOT NULL COMMENT '事件唯一标识',
    event_type VARCHAR(128) NOT NULL COMMENT '事件类型',
    event_data JSON NOT NULL COMMENT '事件数据',
    biz_id VARCHAR(128) COMMENT '业务ID',
    source_system VARCHAR(64) COMMENT '来源系统',
    status TINYINT DEFAULT 0 COMMENT '状态：0-待处理 1-处理中 2-已完成',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 3. notification_record - 通知记录表
```sql
CREATE TABLE notification_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) COMMENT '事件ID（事件模式）',
    subscription_id BIGINT COMMENT '订阅ID（事件模式）',
    third_party_code VARCHAR(64) COMMENT '第三方系统标识（点对点模式）',
    push_mode VARCHAR(16) NOT NULL COMMENT '推送模式：EVENT/DIRECT',
    callback_url VARCHAR(512) NOT NULL,
    request_body TEXT,
    response_code INT,
    response_body TEXT,
    status TINYINT COMMENT '状态：0-待推送 1-成功 2-失败',
    retry_count INT DEFAULT 0,
    cost_time INT COMMENT '耗时(ms)',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 4. third_party_config - 第三方配置表（点对点模式）
```sql
CREATE TABLE third_party_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) UNIQUE NOT NULL COMMENT '第三方系统唯一标识',
    name VARCHAR(128) NOT NULL,
    api_url VARCHAR(512) NOT NULL,
    method VARCHAR(16) DEFAULT 'POST',
    header_template JSON COMMENT 'Header模板',
    body_template TEXT COMMENT 'Body模板',
    success_condition VARCHAR(256) COMMENT '成功条件(JsonPath)',
    max_retry INT DEFAULT 3,
    retry_strategy VARCHAR(32) COMMENT '重试策略',
    status TINYINT DEFAULT 1
);
```

---

## 🔑 核心功能

### 1. 订阅管理

#### 创建订阅
```bash
POST /api/subscriptions
Content-Type: application/json

{
  "subscriberName": "CRM系统",
  "callbackUrl": "https://crm.example.com/webhook",
  "eventTypes": ["USER_REGISTERED", "ORDER_CREATED"],
  "secretKey": "secret123",
  "description": "CRM系统订阅用户和订单事件"
}
```

#### 查询订阅列表
```bash
GET /api/subscriptions
```

#### 更新订阅
```bash
PUT /api/subscriptions/{id}
```

#### 启用/禁用订阅
```bash
POST /api/subscriptions/{id}/enable
POST /api/subscriptions/{id}/disable
```

### 2. 事件发布

#### 发布事件（订阅-发布模式）
```bash
POST /api/events/publish
Content-Type: application/json

{
  "eventType": "ORDER_CREATED",
  "data": "{\"orderId\": \"12345\", \"amount\": 100.00}",
  "bizId": "order-12345",
  "sourceSystem": "OrderService"
}
```

**响应**：
```json
{
  "success": true,
  "eventId": "evt_1788708433086_1",
  "message": "事件发布成功，正在推送给订阅者"
}
```

**系统行为**：
1. 保存事件到event表
2. 根据eventType自动查找所有订阅者
3. 异步推送给所有匹配的订阅者
4. 每个订阅者的推送独立重试

### 3. 推送机制

#### 推送流程
```
事件发布
  ↓
EventService.publishEvent()
  ↓
查找订阅者: subscriptionService.findSubscriptionsByEventType()
  ↓
EventPublisher.publishToSubscribers() [@Async异步]
  ↓
为每个订阅者构建PushMessage
  ↓
提交到UnifiedPusher优先级队列
  ↓
工作线程池处理
  ↓
UnifiedHttpClient执行HTTP调用
  ↓
记录结果到notification_record
```

#### 重试策略
- **指数退避**：1s → 2s → 4s → 8s
- **最大重试次数**：3次（可配置）
- **失败处理**：记录到notification_record，状态标记为失败

---

## 🚀 快速开始

### 环境要求
- Java 8+
- Maven 3.6+
- MySQL 5.7+
- （可选）Kafka 2.x

### 构建与运行

#### 1. 克隆代码
```bash
git clone <repository-url>
cd aicoding
```

#### 2. 配置数据库
```bash
# 创建数据库
mysql -u root -p
CREATE DATABASE push_system DEFAULT CHARACTER SET utf8mb4;

# 导入表结构
mysql -u root -p push_system < src/main/resources/db/init.sql
```

#### 3. 配置application.yml
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/push_system?useSSL=false
    username: root
    password: your_password
```

#### 4. 编译运行
```bash
# 编译
mvn clean compile

# 运行
mvn spring-boot:run
```

#### 5. 验证
```bash
# 查询订阅列表
curl http://localhost:8080/api/subscriptions

# 发布测试事件
curl -X POST http://localhost:8080/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "ORDER_CREATED",
    "data": "{\"orderId\": \"12345\"}",
    "bizId": "test-order"
  }'
```

---

## 📖 使用示例

### 示例1：订单事件通知多个系统

#### 步骤1：注册订阅者
```bash
# CRM系统订阅
curl -X POST http://localhost:8080/api/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "subscriberName": "CRM系统",
    "callbackUrl": "https://crm.example.com/webhook",
    "eventTypes": ["ORDER_CREATED"]
  }'

# 物流系统订阅
curl -X POST http://localhost:8080/api/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "subscriberName": "物流系统",
    "callbackUrl": "https://logistics.example.com/webhook",
    "eventTypes": ["ORDER_CREATED", "ORDER_SHIPPED"]
  }'
```

#### 步骤2：发布订单创建事件
```bash
curl -X POST http://localhost:8080/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "ORDER_CREATED",
    "data": "{\"orderId\": \"ORD001\", \"userId\": \"USR123\", \"amount\": 299.00}",
    "bizId": "ORD001",
    "sourceSystem": "OrderService"
  }'
```

#### 系统行为
- 自动找到CRM系统和物流系统两个订阅者
- 并发推送到两个系统的回调URL
- 如果某个系统失败，独立重试，不影响另一个

### 示例2：用户注册后的级联通知

```bash
# 发布用户注册事件
curl -X POST http://localhost:8080/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "USER_REGISTERED",
    "data": "{\"userId\": \"USR999\", \"email\": \"user@example.com\"}",
    "bizId": "USR999",
    "sourceSystem": "UserService"
  }'
```

**自动通知到**：
- CRM系统：创建客户档案
- 邮件系统：发送欢迎邮件
- 积分系统：发放新人积分

---

## 🔧 配置说明

### 订阅者回调接口规范

订阅者需要提供HTTP回调接口接收通知。

#### 请求格式
```
POST {callbackUrl}
Content-Type: application/json

{
  "eventId": "evt_1788708433086_1",
  "eventType": "ORDER_CREATED",
  "eventData": {
    "orderId": "12345",
    "amount": 100.00
  },
  "bizId": "order-12345",
  "timestamp": 1788708433086
}
```

#### 响应要求
```json
{
  "code": 0,
  "message": "success"
}
```

- HTTP状态码：200-299视为成功
- 响应体包含`code: 0`视为业务成功
- 其他情况视为失败，触发重试

---

## 📊 监控与运维

### 日志
系统输出详细的结构化日志：
```
2026-09-06 23:26:05 [main] INFO  c.p.s.service.SubscriptionService - 创建订阅成功, ID: 1, 订阅者: CRM系统
2026-09-06 23:26:05 [main] INFO  c.p.s.core.UnifiedPusher - 启动统一推送执行层，工作线程数: 10
2026-09-06 23:26:05 [PushWorker-1] INFO  c.p.s.core.UnifiedPusher - 开始执行事件推送, ID: 12345
```

### 关键指标
- 事件发布量（按类型统计）
- 通知成功率
- 推送延迟
- 重试次数
- 队列积压

---

## 🎯 设计原则

### 1. 单一职责
- 专注B2B系统集成
- 不包含消息推送（钉钉、企微等）
- 职责清晰，易维护

### 2. 事件驱动
- 发布-订阅模式实现解耦
- 异步处理提高性能
- 支持一对多广播

### 3. 高可靠性
- 事件持久化存储
- 失败自动重试
- 完整的记录追踪

### 4. 易扩展
- 动态订阅管理
- 灵活的配置驱动
- 支持多种HTTP API格式

---

## 📁 项目结构

```
src/main/java/com/push/system/
├── PushSystemApplication.java          # 启动类
├── api/                                # API控制器层
│   ├── SubscriptionController.java     # 订阅管理API
│   └── EventController.java            # 事件发布API
├── service/                            # 业务服务层
│   ├── SubscriptionService.java        # 订阅管理服务
│   ├── EventService.java               # 事件服务
│   └── EventPublisher.java             # 事件发布器
├── core/                               # 核心推送层
│   └── UnifiedPusher.java              # 统一推送器
├── http/                               # HTTP客户端
│   ├── UnifiedHttpClient.java          # HTTP客户端
│   ├── TemplateEngine.java             # 模板引擎
│   └── HttpExecutor.java               # HTTP执行器
├── entity/                             # 实体类
│   ├── Subscription.java               # 订阅实体
│   └── Event.java                      # 事件实体
├── model/                              # 模型类
│   ├── PushMessage.java                # 推送消息
│   ├── ThirdPartyConfig.java           # 第三方配置
│   └── HttpResult.java                 # HTTP结果
├── queue/                              # 队列
│   └── PriorityPushQueue.java          # 优先级队列
├── id/                                 # ID生成
│   └── IdGenerator.java                # 雪花ID生成器
└── config/                             # 配置类
    ├── AsyncConfig.java                # 异步配置
    └── JacksonConfig.java              # Jackson配置
```

---

## 🔄 版本历史

### v2.0 (2026-09-06) - 当前版本
- ✅ 实现订阅-发布模式
- ✅ 实现订阅管理（CRUD）
- ✅ 实现事件发布API
- ✅ 实现自动订阅者匹配
- ✅ 实现异步推送
- ✅ 支持事件驱动和点对点两种模式
- ✅ 统一HTTP客户端（模板引擎）
- ✅ 优先级队列
- ✅ 失败重试机制

### v1.0 (初始版本)
- 基础架构搭建
- 点对点推送模式

---

## 📚 相关文档

- [01-需求理解文档.md](./01-需求理解文档.md) - 详细需求分析
- [02-产品设计文档.md](./02-产品设计文档.md) - 产品设计
- [03-技术设计文档.md](./03-技术设计文档.md) - 技术架构设计
- [04-设计更新说明.md](./04-设计更新说明.md) - 设计调整说明

---

## 🤝 贡献指南

欢迎贡献代码和反馈问题！

### 开发规范
- 遵循阿里巴巴Java开发规范
- 代码提交前运行测试
- 提交信息清晰明确

---

## 📄 许可证

本项目采用 MIT 许可证

---

**文档状态**: ✅ 最新版本  
**最后更新**: 2026-09-06  
**维护者**: 开发团队
