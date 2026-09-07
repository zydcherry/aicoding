# 推送系统消息路由机制说明

## 概述

当前系统支持**两种推送模式**，通过不同的机制来决定消息应该发送到哪个第三方：

1. **事件驱动模式（Event-Driven）**：基于订阅机制，自动路由
2. **点对点模式（Direct Push）**：基于第三方配置，手动指定

---

## 一、事件驱动模式（订阅-发布模式）

### 工作流程

```
事件发布 → 查找订阅者 → 推送给所有订阅者
```

### 详细步骤

#### 1. 事件发布（EventService.publishEvent）
```java
// API调用发布事件
eventService.publishEvent("ORDER_CREATED", eventData, bizId, sourceSystem);
```

#### 2. 查找订阅者（SubscriptionService.findSubscriptionsByEventType）
```java
// 根据事件类型查找所有订阅者
List<Subscription> subscriptions = subscriptionService.findSubscriptionsByEventType("ORDER_CREATED");
```

**查找逻辑**：
- 从内存索引`eventTypeIndex`中快速查找：`Map<String, List<Long>>`
- 索引存储：`eventType → [subscriptionId1, subscriptionId2, ...]`
- 只返回`status=1`（启用状态）的订阅

**示例**：
```
事件类型：ORDER_CREATED
找到订阅者：
  - CRM系统（订阅了USER_REGISTERED, ORDER_CREATED）
  - 物流系统（订阅了ORDER_CREATED, ORDER_SHIPPED）
```

#### 3. 构建推送消息（EventPublisher.buildPushMessage）
为每个订阅者构建独立的推送消息：

```java
PushMessage message = new PushMessage();
message.setAccessType("EVENT");  // 标识为事件驱动模式
message.setThirdPartyCode("subscription_" + subscription.getId());

// 扩展信息中保存订阅者的回调信息
Map<String, Object> extInfo = new HashMap<>();
extInfo.put("eventId", event.getEventId());
extInfo.put("subscriptionId", subscription.getId());
extInfo.put("subscriberName", subscription.getSubscriberName());
extInfo.put("callbackUrl", subscription.getCallbackUrl());  // ← 关键：订阅者的回调URL
extInfo.put("secretKey", subscription.getSecretKey());
message.setExtInfo(objectMapper.writeValueAsString(extInfo));
```

#### 4. 推送执行（UnifiedPusher.executePushForEvent）
```java
// 从extInfo中提取回调URL和密钥
Map<String, Object> extInfo = objectMapper.readValue(message.getExtInfo(), Map.class);
String callbackUrl = (String) extInfo.get("callbackUrl");  // 订阅者的回调URL
String secretKey = (String) extInfo.get("secretKey");

// 构建临时配置并执行HTTP调用
ThirdPartyConfig config = buildConfigForCallback(callbackUrl, secretKey);
HttpResult result = httpClient.call(message, config);
```

### 路由依据

**事件驱动模式的路由依据是：事件类型（eventType）**

```
订阅关系决定路由：
  订阅表 subscription:
    - id: 1, subscriber_name: "CRM系统", event_types: ["USER_REGISTERED", "ORDER_CREATED"]
    - id: 2, subscriber_name: "物流系统", event_types: ["ORDER_CREATED", "ORDER_SHIPPED"]
    - id: 3, subscriber_name: "财务系统", event_types: ["ORDER_PAID"]

  当发布事件 ORDER_CREATED 时：
    → 自动推送给：CRM系统、物流系统
    → 不会推送给：财务系统（未订阅此事件）
```

### 特点

✅ **自动路由**：根据订阅关系自动确定接收者
✅ **一对多**：一个事件可以推送给多个订阅者
✅ **解耦**：发布者不需要知道订阅者是谁
✅ **动态**：可以随时添加/删除订阅者

---

## 二、点对点模式（直接推送）

### 工作流程

```
API调用 → 指定第三方code → 根据配置推送
```

### 详细步骤

#### 1. API调用指定第三方
```java
PushMessage message = new PushMessage();
message.setThirdPartyCode("supplier-a");  // ← 关键：明确指定第三方编码
message.setAccessType("API_DIRECT");
message.setContent(jsonData);

unifiedPusher.submitForDirectPush(message);
```

#### 2. 查找第三方配置（ThirdPartyConfigService.getConfig）
```java
// 根据第三方编码查找配置
ThirdPartyConfig config = configService.getConfig("supplier-a");
```

**配置包含**：
- API地址：`https://api.supplier-a.com/order/push`
- HTTP方法：`POST`
- Header模板：`{"Content-Type":"application/json", "X-API-Key":"..."}`
- Body模板：`{"orderId":"${message.bizId}", "data":${message.content}}`
- 认证参数：`apiKey`、签名密钥等
- 重试策略：最大重试次数、重试间隔
- 限流配置：QPS限制、日调用量限制

#### 3. 推送执行（UnifiedPusher.executePushForDirect）
```java
String thirdPartyCode = message.getThirdPartyCode();  // "supplier-a"

// 获取第三方配置
ThirdPartyConfig config = configService.getConfig(thirdPartyCode);

// 检查配置是否存在和启用
if (config == null || config.getStatus() != 1) {
    logger.error("第三方配置不存在或已停用");
    return;
}

// 使用配置执行HTTP调用
HttpResult result = httpClient.call(message, config);
```

### 路由依据

**点对点模式的路由依据是：第三方编码（thirdPartyCode）**

```
第三方配置表 third_party_config:
  - code: "supplier-a", name: "供应商A系统", api_url: "https://api.supplier-a.com/..."
  - code: "supplier-b", name: "供应商B系统", api_url: "https://api.supplier-b.com/..."
  - code: "warehouse-sys", name: "仓储系统", api_url: "https://warehouse.internal.com/..."

发送消息时必须指定：
  message.setThirdPartyCode("supplier-a");  → 推送给供应商A
  message.setThirdPartyCode("warehouse-sys"); → 推送给仓储系统
```

### 特点

✅ **明确指定**：调用方明确指定接收方
✅ **一对一**：一条消息只发给一个第三方
✅ **配置驱动**：推送行为完全由配置决定
✅ **灵活定制**：每个第三方可以有独立的认证、模板、重试策略

---

## 三、两种模式对比

| 维度 | 事件驱动模式 | 点对点模式 |
|------|------------|-----------|
| **路由依据** | 事件类型（eventType） | 第三方编码（thirdPartyCode） |
| **路由方式** | 自动查找订阅者 | 手动指定接收方 |
| **接收者数量** | 一对多（0-N个订阅者） | 一对一 |
| **配置位置** | subscription表 | third_party_config表 |
| **目标地址** | subscription.callback_url | third_party_config.api_url |
| **适用场景** | 内部系统集成、事件通知 | 外部API调用、点对点推送 |
| **耦合度** | 低（发布者不知道订阅者） | 中（调用方需指定接收方） |
| **扩展性** | 高（随时增减订阅者） | 中（需添加新配置） |

---

## 四、识别机制

### UnifiedPusher的识别逻辑

```java
private void executePush(PushMessage message) {
    String accessType = message.getAccessType();
    
    if ("EVENT".equals(accessType)) {
        // 事件驱动模式：从extInfo中提取回调URL
        executePushForEvent(message);
    } else {
        // 点对点模式：使用第三方配置
        executePushForDirect(message);
    }
}
```

**关键字段**：
- `accessType = "EVENT"` → 事件驱动模式
- `accessType = "API_DIRECT"` / `"API_KAFKA"` / `"KAFKA_DIRECT"` → 点对点模式

---

## 五、典型使用场景

### 场景1：内部系统事件通知（事件驱动）

**需求**：用户下单后，需要通知CRM系统、物流系统、财务系统

**实现**：
```java
// 1. 各系统提前配置订阅
Subscription sub1 = new Subscription();
sub1.setSubscriberName("CRM系统");
sub1.setCallbackUrl("https://crm.internal.com/webhook");
sub1.setEventTypes(Arrays.asList("ORDER_CREATED"));
subscriptionService.createSubscription(sub1);

Subscription sub2 = new Subscription();
sub2.setSubscriberName("物流系统");
sub2.setCallbackUrl("https://logistics.internal.com/webhook");
sub2.setEventTypes(Arrays.asList("ORDER_CREATED"));
subscriptionService.createSubscription(sub2);

// 2. 订单服务发布事件
eventService.publishEvent("ORDER_CREATED", orderJson, orderId, "order-service");

// 3. 系统自动推送给所有订阅者（CRM、物流）
```

### 场景2：向供应商推送订单（点对点）

**需求**：向特定供应商推送订单数据

**实现**：
```java
// 1. 提前配置供应商信息
ThirdPartyConfig config = new ThirdPartyConfig();
config.setCode("supplier-a");
config.setApiUrl("https://api.supplier-a.com/order/push");
config.setAuthType("apikey");
thirdPartyConfigService.putConfig(config);

// 2. 业务代码明确指定推送给供应商A
PushMessage message = new PushMessage();
message.setThirdPartyCode("supplier-a");  // ← 明确指定
message.setContent(orderJson);
unifiedPusher.submitForDirectPush(message);

// 3. 系统根据supplier-a的配置执行推送
```

---

## 六、混合使用

两种模式可以同时存在：

```java
// 同时支持事件驱动和点对点

// 事件驱动：通知内部系统
eventService.publishEvent("ORDER_CREATED", orderData, orderId, "order-service");
// → 自动推送给：CRM、物流、财务（根据订阅）

// 点对点：推送给外部供应商
PushMessage message = new PushMessage();
message.setThirdPartyCode("supplier-a");
message.setContent(orderData);
unifiedPusher.submitForDirectPush(message);
// → 明确推送给：供应商A
```

---

## 七、当前设计的优缺点

### 优点

✅ **灵活**：支持两种推送模式，覆盖不同场景
✅ **解耦**：事件驱动模式实现发布-订阅解耦
✅ **可配置**：推送行为完全配置驱动
✅ **统一处理**：两种模式共用UnifiedPusher和队列

### 潜在改进点

⚠️ **订阅模式的目标识别不够明确**：
- 当前使用`thirdPartyCode = "subscription_" + subscriptionId`
- 实际不走third_party_config表，而是从extInfo提取callbackUrl
- 建议：添加独立的推送模式标识，避免混淆

⚠️ **缺少订阅者分组**：
- 当前只能按事件类型订阅
- 无法实现"只订阅特定业务线的ORDER_CREATED事件"
- 建议：增加订阅过滤条件（如：bizType、region等）

⚠️ **点对点模式需要预先配置**：
- 新增第三方需要先配置，才能推送
- 建议：支持动态配置或配置模板

---

## 总结

**消息如何路由到第三方？**

1. **事件驱动模式**：
   - 根据`事件类型（eventType）`查找订阅者
   - 从`subscription表`获取`callback_url`
   - 自动推送给所有匹配的订阅者

2. **点对点模式**：
   - 根据`第三方编码（thirdPartyCode）`查找配置
   - 从`third_party_config表`获取`api_url`和推送配置
   - 推送给指定的第三方

**关键差异**：
- 事件驱动：**"我发布了什么"** → 系统自动找订阅者
- 点对点：**"我要推给谁"** → 明确指定接收方
