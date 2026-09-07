# API通知系统

[![Java](https://img.shields.io/badge/Java-8+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.14-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

一个基于事件驱动的B2B业务系统集成平台，支持订阅-发布模式和点对点推送。

## ✨ 核心特性

- 📢 **订阅-发布模式** - 事件自动匹配订阅者，一对多广播
- 🎯 **点对点推送** - 精确指定目标系统
- 🔄 **异步推送** - 高性能、非阻塞
- 🔁 **智能重试** - 失败自动重试，指数退避
- ⚡ **优先级队列** - 支持HIGH/NORMAL/LOW三级优先级
- 🔧 **配置驱动** - HTTP模板引擎，适配任意第三方API格式
- 📊 **完整追踪** - 通知记录可查询

## 🏗️ 架构概览

```
业务系统 → 发布事件 → 通知系统 → 自动匹配订阅者 → 异步推送
                                          ↓
                          ┌───────────────┼───────────────┐
                          ↓               ↓               ↓
                       CRM系统         物流系统         财务系统
```

**支持两种模式**：
1. **订阅-发布模式**：业务系统发布事件，系统自动推送给所有订阅者
2. **点对点推送**：业务系统指定目标，直接推送

## 🚀 快速开始

### 环境要求
- Java 8+
- Maven 3.6+
- MySQL 5.7+

### 5分钟运行

```bash
# 1. 克隆代码
git clone <repository-url>
cd aicoding

# 2. 配置数据库
mysql -u root -p
CREATE DATABASE push_system DEFAULT CHARACTER SET utf8mb4;
mysql -u root -p push_system < src/main/resources/db/init.sql

# 3. 修改配置（application.yml）
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/push_system
    username: root
    password: your_password

# 4. 启动应用
mvn spring-boot:run

# 5. 测试
curl http://localhost:8080/api/subscriptions
```

## 📖 使用示例

### 1. 注册订阅

```bash
curl -X POST http://localhost:8080/api/subscriptions \
  -H "Content-Type: application/json" \
  -d '{
    "subscriberName": "CRM系统",
    "callbackUrl": "https://crm.example.com/webhook",
    "eventTypes": ["ORDER_CREATED", "USER_REGISTERED"]
  }'
```

### 2. 发布事件

```bash
curl -X POST http://localhost:8080/api/events/publish \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "ORDER_CREATED",
    "data": "{\"orderId\": \"12345\", \"amount\": 100.00}",
    "bizId": "order-12345"
  }'
```

**系统行为**：
- 自动查找订阅了`ORDER_CREATED`的所有系统
- 异步并发推送给每个订阅者
- 失败自动重试，记录完整日志

### 3. 查询订阅

```bash
# 查询所有订阅
curl http://localhost:8080/api/subscriptions

# 查询单个订阅
curl http://localhost:8080/api/subscriptions/1
```

## 📊 核心API

| API | 方法 | 说明 |
|-----|------|------|
| `/api/subscriptions` | POST | 创建订阅 |
| `/api/subscriptions` | GET | 查询订阅列表 |
| `/api/subscriptions/{id}` | GET | 查询单个订阅 |
| `/api/subscriptions/{id}` | PUT | 更新订阅 |
| `/api/subscriptions/{id}` | DELETE | 删除订阅 |
| `/api/subscriptions/{id}/enable` | POST | 启用订阅 |
| `/api/subscriptions/{id}/disable` | POST | 禁用订阅 |
| `/api/events/publish` | POST | 发布事件 |
| `/api/events/{eventId}` | GET | 查询事件状态 |

## 💾 数据库表

- **subscription** - 订阅表：存储订阅者信息和订阅的事件类型
- **event** - 事件表：存储发布的事件
- **notification_record** - 通知记录表：记录每次推送的详细信息
- **third_party_config** - 第三方配置表：点对点模式的配置

## 📁 项目结构

```
src/main/java/com/push/system/
├── api/              # REST API控制器
├── service/          # 业务服务层
├── core/             # 核心推送引擎
├── http/             # HTTP客户端
├── entity/           # 实体类
├── model/            # 数据模型
├── queue/            # 优先级队列
└── config/           # 配置类
```

## 🎯 典型场景

### 场景1：订单创建通知
```
订单系统发布 ORDER_CREATED 事件
  ↓
自动推送给：CRM系统、物流系统、财务系统
  ↓
各系统独立处理：创建客户档案、准备发货、记账
```

### 场景2：用户注册通知
```
用户服务发布 USER_REGISTERED 事件
  ↓
自动推送给：CRM系统、邮件系统、积分系统
  ↓
各系统独立处理：创建档案、发欢迎邮件、发放积分
```

## 🔧 配置说明

### 订阅者回调接口规范

订阅者需要实现HTTP回调接口：

```
POST {callbackUrl}
Content-Type: application/json

{
  "eventId": "evt_xxx",
  "eventType": "ORDER_CREATED",
  "eventData": {...},
  "bizId": "order-123",
  "timestamp": 1234567890
}
```

**响应要求**：
- HTTP 200-299 且 `{"code": 0}` 视为成功
- 其他情况触发重试

## 🔄 重试机制

- **策略**：指数退避（1s → 2s → 4s → 8s）
- **次数**：默认最多3次
- **独立重试**：每个订阅者的推送独立重试，互不影响

## 📚 文档

完整文档请查看：

- [📖 项目文档](docs/README.md) - 完整的系统文档
- [📋 需求理解文档](docs/01-需求理解文档.md) - 详细需求分析
- [🎨 产品设计文档](docs/02-产品设计文档.md) - 产品设计
- [🏗️ 技术设计文档](docs/03-技术设计文档.md) - 技术架构
- [🔄 设计更新说明](docs/04-设计更新说明.md) - 设计演进

## 🛠️ 技术栈

- **框架**：Spring Boot 2.7.14
- **数据库**：MySQL 5.7+
- **持久层**：MyBatis 2.3.1
- **消息队列**：Kafka（可选）
- **JSON处理**：Jackson
- **HTTP客户端**：RestTemplate
- **日志**：SLF4J + Logback

## 🎯 设计理念

- **单一职责**：专注B2B系统集成，不混入IM消息推送
- **事件驱动**：基于事件的松耦合架构
- **配置驱动**：通过配置适配不同的API格式
- **高可靠性**：持久化存储、自动重试、完整追踪

## 🤝 贡献

欢迎贡献代码和反馈问题！

## 📄 许可证

MIT License

---

**开发团队** | 2026-09-06
