# B2B系统集成 - 第三方API推送系统设计文档

## 1. 系统概述

### 1.1 系统定位
本系统是一个面向B2B场景的第三方API调用推送系统，用于与外部合作伙伴系统进行数据交互，如订单推送、库存同步、物流通知等业务场景。

### 1.2 核心目标
- 提供多种接入方式，灵活适配不同业务场景
- 支持优先级调度，保障核心业务优先处理
- 统一的第三方API调用管理，屏蔽各系统差异
- 完善的失败重试、告警和追踪机制
- 可配置、可扩展的第三方系统适配

## 2. 系统架构

### 2.1 总体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         调用方系统                                │
│              (订单系统、库存系统、物流系统等)                      │
└───────┬─────────────────────────────────┬───────────────────────┘
        │                                 │
        │ 方式1: HTTP直接推送              │ 方式2: HTTP转Kafka
        │                                 │
        ▼                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│                      接入层 (Ingress Layer)                       │
│  ┌──────────────────┐              ┌──────────────────┐         │
│  │  HTTP API网关     │              │  Kafka Producer   │         │
│  │  - 参数校验       │              │  - 消息发送       │         │
│  │  - ID生成         │              │  - 主题路由       │         │
│  │  - 同步响应       │              │  - 失败处理       │         │
│  └────────┬─────────┘              └────────┬─────────┘         │
└───────────┼──────────────────────────────────┼──────────────────┘
            │                                  │
            │                                  │ 方式3: 外部Kafka
            │                                  │      ▼
            │                          ┌──────────────────┐
            │                          │ Kafka Consumer    │
            │                          │ - 消息消费        │
            │                          │ - ID生成          │
            │                          └────────┬─────────┘
            │                                   │
            └───────────────┬───────────────────┘
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    优先级队列层 (Queue Layer)                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │           PriorityBlockingQueue (内存队列)                 │   │
│  │   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │   │
│  │   │ 高优先级队列 │  │ 普通优先级   │  │ 低优先级队列 │     │   │
│  │   │   (Level 1) │  │  (Level 2)   │  │  (Level 3)  │     │   │
│  │   └─────────────┘  └─────────────┘  └─────────────┘     │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                  推送执行层 (Execution Layer)                     │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              Worker Thread Pool (工作线程池)              │   │
│  │   ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐       │   │
│  │   │Worker-1│  │Worker-2│  │Worker-3│  │Worker-N│       │   │
│  │   └───┬────┘  └───┬────┘  └───┬────┘  └───┬────┘       │   │
│  └───────┼───────────┼───────────┼───────────┼────────────┘   │
└──────────┼───────────┼───────────┼───────────┼─────────────────┘
           │           │           │           │
           └───────────┴───────────┴───────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────────┐
│               HTTP调用层 (HTTP Client Layer)                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │            统一HTTP客户端 (HttpClient)                     │   │
│  │  - 构建HTTP请求 (URL/Header/Body)                         │   │
│  │  - 执行HTTP调用                                            │   │
│  │  - 解析响应结果                                            │   │
│  │  - 超时控制、连接池管理                                     │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
                     ┌──────────────────┐
                     │  第三方配置管理   │
                     │  (动态加载配置)   │
                     └──────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
        ▼                      ▼                      ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  供应商A配置  │      │  供应商B配置  │      │  供应商C配置  │
│  - API地址   │      │  - API地址   │      │  - API地址   │
│  - Header模板│      │  - Header模板│      │  - Header模板│
│  - Body模板  │      │  - Body模板  │      │  - Body模板  │
│  - 响应解析  │      │  - 响应解析  │      │  - 响应解析  │
└──────┬───────┘      └──────┬───────┘      └──────┬───────┘
       │                     │                     │
       └─────────────────────┴─────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      第三方系统 (HTTP API)                        │
│     供应商A系统          供应商B系统          供应商C系统          │
└─────────────────────────────────────────────────────────────────┘

           ┌─────────────────────────────────────┐
           │      支撑层 (Support Layer)         │
           ├─────────────────────────────────────┤
           │  • 推送记录持久化 (MySQL/Redis)     │
           │  • 失败重试机制                     │
           │  • 告警服务 (邮件/钉钉/短信)        │
           │  • 监控指标采集 (Prometheus)        │
           │  • 全链路追踪 (分布式追踪ID)        │
           └─────────────────────────────────────┘
```

### 2.2 架构分层说明

#### 2.2.1 接入层 (Ingress Layer)
**职责**：提供统一的接入入口，屏蔽下游复杂性

**方式1 - HTTP直接推送**
- 接口：`POST /api/push/direct`
- 流程：接收请求 → 生成ID → 入队 → 同步返回ID
- 特点：低延迟，适合实时性要求高的场景
- 响应时间：< 100ms

**方式2 - HTTP转Kafka**
- 接口：`POST /api/push/async`
- 流程：接收请求 → 生成ID → 发送Kafka → 同步返回ID
- 特点：削峰填谷，适合高吞吐场景
- 响应时间：< 50ms（仅写Kafka）

**方式3 - 直接消费Kafka**
- 来源：外部系统的Kafka主题
- 流程：消费消息 → 生成ID → 入队
- 特点：事件驱动，适合解耦场景

#### 2.2.2 优先级队列层 (Queue Layer)
**职责**：按优先级排队，保障核心业务优先处理

- 三级优先级：HIGH(1) > NORMAL(2) > LOW(3)
- 队列实现：PriorityBlockingQueue
- 排序策略：优先级 → 创建时间（FIFO）
- 容量配置：可根据系统负载调整（默认10000）

#### 2.2.3 推送执行层 (Execution Layer)
**职责**：从队列取消息，调度执行推送任务

- 线程池模型：固定大小线程池（可配置）
- 工作模式：循环从队列取消息并执行
- 超时控制：轮询超时、推送超时分别配置
- 限流控制：全局QPS限制 + 单第三方限流

#### 2.2.4 HTTP调用层 (HTTP Client Layer)
**职责**：统一的HTTP客户端，根据配置调用不同第三方系统

**核心功能**：
1. **请求构建**：根据配置模板动态构建HTTP请求（URL、Header、Body）
2. **HTTP调用**：统一的HTTP客户端执行调用
3. **响应解析**：根据配置规则解析不同格式的响应
4. **连接管理**：连接池、超时控制、Keep-Alive
5. **配置驱动**：所有第三方差异通过配置屏蔽

## 3. 三种接入方式详细设计

### 3.1 方式1：HTTP直接推送

```
┌──────────┐
│ 调用方   │
└────┬─────┘
     │ POST /api/push/direct
     ▼
┌────────────────┐
│ PushController │ ─────① 参数校验
└────┬───────────┘
     │
     ▼
┌────────────────┐
│ IdGenerator    │ ─────② 生成唯一ID (标识位=0)
└────┬───────────┘
     │
     ▼
┌────────────────┐
│ UnifiedPusher  │ ─────③ 消息入优先级队列
└────┬───────────┘
     │
     ▼
┌────────────────┐
│ 返回响应        │ ─────④ 返回pushId给调用方
│ {pushId:xxx}   │
└────────────────┘
     │
     ▼
┌────────────────┐
│ Worker线程     │ ─────⑤ 从队列取出消息
└────┬───────────┘
     │
     ▼
┌────────────────┐
│ ThirdParty     │ ─────⑥ 调用第三方适配器
│ Adapter        │
└────┬───────────┘
     │
     ▼
┌────────────────┐
│ 第三方系统      │ ─────⑦ 推送到外部系统
└────────────────┘
```

### 3.2 方式2：HTTP转Kafka

```
┌──────────┐
│ 调用方   │
└────┬─────┘
     │ POST /api/push/async
     ▼
┌────────────────┐
│ PushController │ ─────① 参数校验
└────┬───────────┘
     │
     ▼
┌────────────────┐
│ IdGenerator    │ ─────② 生成唯一ID (标识位=0)
└────┬───────────┘
     │
     ▼
┌────────────────┐
│ KafkaProducer  │ ─────③ 发送到内部Kafka（按优先级分topic）
└────┬───────────┘       push-high / push-normal / push-low
     │
     ▼
┌────────────────┐
│ 返回响应        │ ─────④ 返回pushId给调用方
│ {pushId:xxx}   │
└────────────────┘
     
     (异步流程)
     
┌────────────────┐
│ KafkaConsumer  │ ─────⑤ 消费Kafka消息
└────┬───────────┘
     │
     ▼
┌────────────────┐
│ UnifiedPusher  │ ─────⑥ 消息入优先级队列
└────┬───────────┘
     │
     ▼
     (后续流程同方式1)
```

### 3.3 方式3：外部Kafka消费

```
┌────────────────┐
│ 外部系统Kafka  │
└────┬───────────┘
     │ Topic: external-push-events
     ▼
┌────────────────┐
│ KafkaConsumer  │ ─────① 消费外部Kafka消息
└────┬───────────┘
     │
     ▼
┌────────────────┐
│ IdGenerator    │ ─────② 生成唯一ID (标识位=1)
└────┬───────────┘
     │
     ▼
┌────────────────┐
│ UnifiedPusher  │ ─────③ 消息入优先级队列
└────┬───────────┘
     │
     ▼
     (后续流程同方式1)
```

## 4. 唯一ID生成策略

### 4.1 ID结构（64位）

```
┌─────────────┬────────────┬──────────┬─────────────┐
│ 时间戳(41位) │ 机器ID(10位) │ 序列号(11位) │ 接入方式(1位) │
└─────────────┴────────────┴──────────┴─────────────┘
  起始时间: 2024-01-01
  支持到: 2093年
  机器数: 1024台
  并发: 2048/ms
```

### 4.2 接入方式标识
- **0**: API接入（方式1和方式2）
- **1**: Kafka直接消费（方式3）

### 4.3 ID功能
1. **全局唯一**：分布式环境下不重复
2. **时间有序**：可按生成时间排序
3. **来源追溯**：从ID反推接入方式
4. **对账支持**：业务对账的唯一凭证

## 5. 第三方HTTP调用设计（核心）

### 5.1 设计思路

**所有第三方系统都通过HTTP协议调用**，不同供应商的差异体现在：
- **API地址不同**：每个供应商有独立的API端点
- **请求Header不同**：认证方式、自定义Header字段
- **请求Body格式不同**：JSON结构、字段映射关系
- **响应结果不同**：响应格式、成功失败判断逻辑

**解决方案**：统一HTTP客户端 + 配置化管理

### 5.2 统一HTTP客户端

```java
public class UnifiedHttpClient {
    /**
     * 调用第三方HTTP API
     */
    public HttpResult call(PushMessage message, ThirdPartyConfig config) {
        // 1. 构建请求URL
        String url = buildUrl(config);
        
        // 2. 构建请求Header
        Map<String, String> headers = buildHeaders(message, config);
        
        // 3. 构建请求Body
        String body = buildBody(message, config);
        
        // 4. 执行HTTP调用
        HttpResponse response = executeHttp(url, headers, body, config);
        
        // 5. 解析响应结果
        return parseResponse(response, config);
    }
}
```

### 5.3 第三方配置模型

```java
public class ThirdPartyConfig {
    // 基本信息
    private String code;              // 第三方系统编码（唯一标识）
    private String name;              // 第三方系统名称
    
    // HTTP配置
    private String apiUrl;            // API完整地址，如: https://api.supplier-a.com/push
    private String method;            // HTTP方法: POST/PUT/GET
    private Integer connectTimeout;   // 连接超时（ms）
    private Integer readTimeout;      // 读取超时（ms）
    
    // Header配置（模板化）
    private Map<String, String> headerTemplate;  
    // 示例:
    // {
    //   "Content-Type": "application/json",
    //   "X-API-Key": "${config.apiKey}",
    //   "X-Timestamp": "${timestamp}",
    //   "X-Sign": "${sign}"
    // }
    
    // Body配置（模板化）
    private String bodyTemplate;      
    // 示例（JSON）:
    // {
    //   "orderId": "${message.bizId}",
    //   "orderData": ${message.content},
    //   "timestamp": "${timestamp}"
    // }
    
    // 响应解析配置
    private String successCondition;  // 成功条件表达式，如: $.code == 200
    private String resultPath;        // 结果路径，如: $.data
    private String errorMsgPath;      // 错误信息路径，如: $.message
    
    // 认证配置
    private String authType;          // 认证类型: none/apikey/sign/oauth2
    private Map<String, String> authParams; 
    // 示例（签名认证）:
    // {
    //   "apiKey": "xxx",
    //   "secretKey": "yyy",
    //   "signAlgorithm": "MD5"
    // }
    
    // 重试配置
    private Integer maxRetry;         // 最大重试次数
    private String retryStrategy;     // 重试策略: fixed/exponential
    private Integer retryInterval;    // 重试间隔（ms）
    
    // 限流配置
    private Integer qpsLimit;         // QPS限制
    private Integer dailyLimit;       // 日调用量限制
    
    // 告警配置
    private Integer failAlertThreshold; // 失败告警阈值
    private String alertReceivers;    // 告警接收人
    
    // 扩展字段
    private Map<String, Object> extConfig; // 扩展配置
}
```

### 5.4 配置示例

#### 示例1：供应商A（API Key认证 + JSON）

```json
{
  "code": "supplier-a",
  "name": "供应商A系统",
  "apiUrl": "https://api.supplier-a.com/order/push",
  "method": "POST",
  "connectTimeout": 3000,
  "readTimeout": 5000,
  "headerTemplate": {
    "Content-Type": "application/json",
    "X-API-Key": "${config.apiKey}",
    "X-Request-Id": "${message.id}"
  },
  "bodyTemplate": "{\"orderId\":\"${message.bizId}\",\"data\":${message.content},\"timestamp\":${timestamp}}",
  "successCondition": "$.code == 0",
  "resultPath": "$.data",
  "errorMsgPath": "$.msg",
  "authType": "apikey",
  "authParams": {
    "apiKey": "ak_supplier_a_xxx"
  },
  "maxRetry": 3,
  "retryStrategy": "exponential"
}
```

**请求示例**：
```http
POST https://api.supplier-a.com/order/push
Content-Type: application/json
X-API-Key: ak_supplier_a_xxx
X-Request-Id: 1234567890

{
  "orderId": "ORDER123",
  "data": {"item": "商品A", "qty": 10},
  "timestamp": 1699999999000
}
```

**响应示例**：
```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "thirdOrderId": "TPO123456"
  }
}
```

#### 示例2：供应商B（签名认证 + 不同JSON结构）

```json
{
  "code": "supplier-b",
  "name": "供应商B系统",
  "apiUrl": "https://open.supplier-b.com/api/v2/sync",
  "method": "POST",
  "connectTimeout": 5000,
  "readTimeout": 10000,
  "headerTemplate": {
    "Content-Type": "application/json",
    "App-Key": "${config.appKey}",
    "Timestamp": "${timestamp}",
    "Sign": "${sign}"
  },
  "bodyTemplate": "{\"bizType\":\"ORDER\",\"bizNo\":\"${message.bizId}\",\"content\":${message.content}}",
  "successCondition": "$.success == true",
  "resultPath": "$.result",
  "errorMsgPath": "$.errorMsg",
  "authType": "sign",
  "authParams": {
    "appKey": "app_supplier_b_xxx",
    "secretKey": "secret_yyy",
    "signAlgorithm": "MD5"
  },
  "maxRetry": 5,
  "retryStrategy": "exponential"
}
```

**签名算法**：
```
sign = MD5(appKey + timestamp + body + secretKey)
```

**请求示例**：
```http
POST https://open.supplier-b.com/api/v2/sync
Content-Type: application/json
App-Key: app_supplier_b_xxx
Timestamp: 1699999999000
Sign: abc123def456...

{
  "bizType": "ORDER",
  "bizNo": "ORDER123",
  "content": {"item": "商品A", "qty": 10}
}
```

**响应示例**：
```json
{
  "success": true,
  "result": {
    "syncId": "SYNC789"
  },
  "errorMsg": null
}
```

#### 示例3：供应商C（OAuth2.0认证）

```json
{
  "code": "supplier-c",
  "name": "供应商C系统",
  "apiUrl": "https://api.supplier-c.com/push/order",
  "method": "POST",
  "connectTimeout": 3000,
  "readTimeout": 5000,
  "headerTemplate": {
    "Content-Type": "application/json",
    "Authorization": "Bearer ${token}"
  },
  "bodyTemplate": "{\"order_id\":\"${message.bizId}\",\"order_info\":${message.content}}",
  "successCondition": "$.status == 'SUCCESS'",
  "resultPath": "$.data",
  "errorMsgPath": "$.error.message",
  "authType": "oauth2",
  "authParams": {
    "clientId": "client_xxx",
    "clientSecret": "secret_yyy",
    "tokenUrl": "https://auth.supplier-c.com/oauth/token",
    "grantType": "client_credentials"
  },
  "maxRetry": 3,
  "retryStrategy": "fixed"
}
```

### 5.5 模板变量说明

**系统内置变量**：
- `${message.id}`: 推送ID
- `${message.bizId}`: 业务ID
- `${message.content}`: 消息内容（JSON字符串）
- `${timestamp}`: 当前时间戳
- `${sign}`: 签名（根据authType自动生成）
- `${token}`: OAuth2 Token（自动获取和刷新）

**配置变量**：
- `${config.xxx}`: 从authParams中获取配置值
- 如：`${config.apiKey}` 取 authParams.apiKey

### 5.6 请求构建流程

```java
// 1. 替换Header模板中的变量
Map<String, String> headers = new HashMap<>();
for (Map.Entry<String, String> entry : config.getHeaderTemplate().entrySet()) {
    String value = replaceVariables(entry.getValue(), message, config);
    headers.put(entry.getKey(), value);
}

// 2. 替换Body模板中的变量
String body = replaceVariables(config.getBodyTemplate(), message, config);

// 3. 执行HTTP请求
HttpResponse response = httpClient.post(config.getApiUrl(), headers, body, config.getReadTimeout());
```

### 5.7 响应解析流程

```java
// 1. 解析JSON响应
JsonNode responseJson = objectMapper.readTree(response.getBody());

// 2. 判断是否成功（使用JsonPath表达式）
// successCondition: "$.code == 0"
boolean success = evaluateCondition(responseJson, config.getSuccessCondition());

// 3. 提取结果数据
// resultPath: "$.data"
JsonNode result = JsonPath.read(responseJson, config.getResultPath());

// 4. 提取错误信息（如果失败）
// errorMsgPath: "$.msg"
String errorMsg = JsonPath.read(responseJson, config.getErrorMsgPath());
```

### 5.8 认证方式处理

#### 5.8.1 API Key认证
```java
// 直接从配置中获取apiKey，放入Header
headers.put("X-API-Key", config.getAuthParams().get("apiKey"));
```

#### 5.8.2 签名认证
```java
// 根据签名算法生成签名
String appKey = config.getAuthParams().get("appKey");
String secretKey = config.getAuthParams().get("secretKey");
String signAlgorithm = config.getAuthParams().get("signAlgorithm");

String signStr = appKey + timestamp + body + secretKey;
String sign = hash(signStr, signAlgorithm); // MD5/SHA256等

headers.put("App-Key", appKey);
headers.put("Timestamp", timestamp);
headers.put("Sign", sign);
```

#### 5.8.3 OAuth2.0认证
```java
// 1. 检查token是否过期
if (isTokenExpired(config.getCode())) {
    // 2. 重新获取token
    String token = refreshToken(config);
    // 3. 缓存token
    cacheToken(config.getCode(), token);
}

// 4. 使用token
String token = getCachedToken(config.getCode());
headers.put("Authorization", "Bearer " + token);
```

### 5.9 配置管理

第三方系统配置存储在数据库中，支持：
1. **动态加载**：配置变更无需重启
2. **版本管理**：配置变更记录可追溯
3. **灰度发布**：新配置先小流量验证
4. **热更新**：配置刷新不影响正在执行的任务

**配置表设计**：
```sql
CREATE TABLE third_party_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) UNIQUE NOT NULL COMMENT '第三方编码',
    name VARCHAR(100) NOT NULL COMMENT '第三方名称',
    api_url VARCHAR(500) NOT NULL COMMENT 'API地址',
    method VARCHAR(10) DEFAULT 'POST' COMMENT 'HTTP方法',
    connect_timeout INT DEFAULT 3000 COMMENT '连接超时(ms)',
    read_timeout INT DEFAULT 5000 COMMENT '读取超时(ms)',
    header_template JSON COMMENT 'Header模板',
    body_template TEXT COMMENT 'Body模板',
    success_condition VARCHAR(200) COMMENT '成功条件表达式',
    result_path VARCHAR(100) COMMENT '结果路径',
    error_msg_path VARCHAR(100) COMMENT '错误信息路径',
    auth_type VARCHAR(20) COMMENT '认证类型',
    auth_params JSON COMMENT '认证参数',
    max_retry INT DEFAULT 3 COMMENT '最大重试次数',
    retry_strategy VARCHAR(20) DEFAULT 'exponential' COMMENT '重试策略',
    retry_interval INT DEFAULT 1000 COMMENT '重试间隔(ms)',
    qps_limit INT COMMENT 'QPS限制',
    daily_limit INT COMMENT '日调用量限制',
    fail_alert_threshold INT DEFAULT 10 COMMENT '失败告警阈值',
    alert_receivers VARCHAR(500) COMMENT '告警接收人',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
    ext_config JSON COMMENT '扩展配置',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_code (code),
    INDEX idx_status (status)
) COMMENT='第三方系统配置表';
```

## 6. 失败重试机制

### 6.1 重试策略

#### 6.1.1 固定间隔重试（Fixed）
```
第1次失败 → 等待1秒 → 第1次重试
第2次失败 → 等待1秒 → 第2次重试
第3次失败 → 等待1秒 → 第3次重试
```

#### 6.1.2 指数退避重试（Exponential）
```
第1次失败 → 等待2^1秒(2s) → 第1次重试
第2次失败 → 等待2^2秒(4s) → 第2次重试
第3次失败 → 等待2^3秒(8s) → 第3次重试
```

### 6.2 重试触发条件

**需要重试**：
- 网络超时（ConnectTimeout、ReadTimeout）
- 第三方系统5xx错误
- 连接被拒绝
- 第三方返回特定业务错误码（可配置）

**不重试**：
- 参数校验失败（4xx错误）
- 业务逻辑错误（如订单已存在）
- 认证失败
- 消息已过期

### 6.3 重试流程

```
┌──────────────┐
│ 执行推送     │
└──────┬───────┘
       │
       ▼
    是否成功？
    /      \
  成功     失败
   │        │
   │        ▼
   │   是否可重试？
   │     /      \
   │   是       否
   │   │         │
   │   ▼         ▼
   │ 重试次数    记录失败
   │ < 最大值？   触发告警
   │  /     \
   │ 是     否
   │ │      │
   │ ▼      ▼
   │等待   记录失败
   │重试   触发告警
   │ │
   │ └──────┐
   │        │
   └────────┼──────→ 记录推送日志
            │        更新状态
            ▼
          结束
```

## 7. 告警机制

### 7.1 告警类型

#### 7.1.1 推送失败告警
- **触发条件**：单条消息重试达到最大次数仍失败
- **告警级别**：高
- **告警内容**：pushId、第三方编码、失败原因、重试次数

#### 7.1.2 失败率告警
- **触发条件**：某第三方系统1分钟内失败率 > 阈值（如30%）
- **告警级别**：紧急
- **告警内容**：第三方编码、失败率、失败数量、时间范围

#### 7.1.3 队列堆积告警
- **触发条件**：队列消息数 > 阈值（如5000）
- **告警级别**：中
- **告警内容**：当前队列深度、容量、使用率

#### 7.1.4 第三方系统不可用告警
- **触发条件**：健康检查连续N次失败
- **告警级别**：紧急
- **告警内容**：第三方编码、失败次数、错误信息

### 7.2 告警渠道
- 钉钉群机器人
- 企业微信群机器人
- 短信告警（紧急级别）
- 邮件告警
- 监控平台（Prometheus AlertManager）

### 7.3 告警配置
```java
public class AlertConfig {
    private String thirdPartyCode;        // 第三方编码
    private Integer failureRateThreshold; // 失败率阈值（%）
    private Integer queueThreshold;       // 队列堆积阈值
    private Integer healthCheckFailCount; // 健康检查失败次数阈值
    private List<String> alertChannels;   // 告警渠道
    private List<String> alertReceivers;  // 告警接收人
}
```

## 8. 记录和追踪

### 8.1 推送记录表

```sql
CREATE TABLE push_record (
    id BIGINT PRIMARY KEY COMMENT '推送ID（雪花算法生成）',
    biz_id VARCHAR(100) COMMENT '业务ID',
    access_type TINYINT NOT NULL COMMENT '接入方式: 0-API 1-Kafka',
    priority TINYINT NOT NULL COMMENT '优先级: 1-HIGH 2-NORMAL 3-LOW',
    third_party_code VARCHAR(50) NOT NULL COMMENT '第三方编码',
    message_type VARCHAR(50) COMMENT '消息类型',
    message_content TEXT COMMENT '消息内容',
    status TINYINT NOT NULL COMMENT '状态: 0-待推送 1-推送中 2-成功 3-失败',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    error_message TEXT COMMENT '错误信息',
    request_data TEXT COMMENT '请求数据',
    response_data TEXT COMMENT '响应数据',
    cost_time INT COMMENT '耗时（ms）',
    expire_time TIMESTAMP COMMENT '过期时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    pushed_at TIMESTAMP COMMENT '推送完成时间',
    INDEX idx_third_party_code (third_party_code),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_biz_id (biz_id)
) COMMENT='推送记录表';
```

### 8.2 失败记录表

```sql
CREATE TABLE push_failure (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    push_id BIGINT NOT NULL COMMENT '推送ID',
    third_party_code VARCHAR(50) NOT NULL COMMENT '第三方编码',
    failure_reason VARCHAR(500) COMMENT '失败原因',
    error_code VARCHAR(50) COMMENT '错误码',
    error_message TEXT COMMENT '错误详情',
    retry_count INT COMMENT '失败时的重试次数',
    request_data TEXT COMMENT '请求数据',
    response_data TEXT COMMENT '响应数据',
    is_alerted TINYINT DEFAULT 0 COMMENT '是否已告警',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_push_id (push_id),
    INDEX idx_third_party_code (third_party_code),
    INDEX idx_created_at (created_at)
) COMMENT='推送失败记录表';
```

### 8.3 全链路追踪

使用 `traceId` 贯穿整个推送链路：

```
traceId: 推送ID（雪花算法生成的64位Long）

日志格式：
[traceId=1234567890] 接收到推送请求
[traceId=1234567890] 消息入队，优先级=HIGH
[traceId=1234567890] Worker-3 开始处理
[traceId=1234567890] 调用第三方系统，code=supplier-a
[traceId=1234567890] 第三方返回成功，耗时=120ms
[traceId=1234567890] 推送完成，总耗时=150ms
```

## 9. 监控指标

### 9.1 核心指标

| 指标名称 | 类型 | 说明 |
|---------|------|------|
| push_total | Counter | 总推送次数 |
| push_success | Counter | 推送成功次数 |
| push_failure | Counter | 推送失败次数 |
| push_duration_seconds | Histogram | 推送耗时分布 |
| push_retry_total | Counter | 重试总次数 |
| queue_size | Gauge | 队列当前深度 |
| queue_wait_seconds | Histogram | 消息在队列中的等待时间 |
| third_party_qps | Gauge | 各第三方系统QPS |
| third_party_success_rate | Gauge | 各第三方系统成功率 |
| http_request_total | Counter | HTTP请求总数 |
| http_request_duration | Histogram | HTTP请求耗时 |

### 9.2 监控维度

- **按第三方系统**：观察各第三方的调用情况
- **按优先级**：高优先级消息的处理时效
- **按接入方式**：三种接入方式的使用分布
- **按时间段**：峰谷时段的流量特征
- **按HTTP状态码**：2xx/4xx/5xx分布

## 10. 性能和容量规划

### 10.1 性能目标

| 场景 | 目标 |
|------|------|
| API接入响应时间 | P99 < 100ms |
| Kafka消息发送 | P99 < 50ms |
| 消息处理延迟 | P99 < 5s（从接收到推送完成） |
| 系统吞吐量 | 支持1000+ QPS |
| 队列容量 | 10000条消息 |
| 第三方调用超时 | 默认5秒（可配置） |

### 10.2 容量评估

**单实例容量**：
- 工作线程：10个（可配置）
- 单线程QPS：约10（假设第三方平均响应100ms）
- 单实例理论QPS：100
- 考虑重试和异常，实际QPS：50-70

**集群部署**：
- 10个实例 → 支持500-700 QPS
- 20个实例 → 支持1000-1400 QPS

### 10.3 扩展策略

1. **水平扩展**：增加实例数量
2. **垂直扩展**：增加单实例工作线程数
3. **队列扩展**：Kafka分区扩容
4. **限流降级**：低优先级消息延迟处理
5. **分库分表**：推送记录表按时间分表

### 10.4 性能优化建议

1. **HTTP连接池**：复用连接，避免频繁建连
2. **Token缓存**：OAuth2 Token缓存，避免频繁刷新
3. **配置缓存**：第三方配置本地缓存，定时刷新
4. **异步日志**：日志异步写入，不阻塞主流程
5. **批量操作**：数据库批量插入/更新

## 11. 部署架构

### 11.1 推荐部署方案

```
┌─────────────────────────────────────────────────────────┐
│                      负载均衡 (Nginx/SLB)                │
└───────────────────┬─────────────────────────────────────┘
                    │
        ┌───────────┼───────────┐
        │           │           │
        ▼           ▼           ▼
   ┌────────┐  ┌────────┐  ┌────────┐
   │ 实例1  │  │ 实例2  │  │ 实例N  │  (推送服务实例)
   └───┬────┘  └───┬────┘  └───┬────┘
       │           │           │
       └───────────┼───────────┘
                   │
          ┌────────┴────────┐
          │                 │
          ▼                 ▼
    ┌──────────┐      ┌──────────┐
    │  Kafka   │      │  MySQL   │  (存储层)
    │  集群    │      │  主从    │
    └──────────┘      └──────────┘
```

### 11.2 高可用保障

1. **服务高可用**：多实例部署，任一实例宕机不影响服务
2. **Kafka高可用**：3副本保证消息不丢失
3. **数据库高可用**：主从架构 + 自动故障切换
4. **限流保护**：防止流量突增导致系统崩溃
5. **熔断降级**：第三方不可用时自动熔断

## 12. 安全设计

### 12.1 API安全
- API鉴权：签名认证或Token认证
- 请求限流：防止恶意刷量
- 参数校验：防止注入攻击

### 12.2 第三方调用安全
- HTTPS传输：敏感数据加密传输
- 证书校验：双向TLS认证（mTLS）
- 密钥管理：密钥加密存储，定期轮换

### 12.3 数据安全
- 敏感字段加密：如身份证号、手机号
- 访问控制：按角色分配数据访问权限
- 审计日志：记录所有敏感操作

## 14. 实现清单

### 14.1 核心模块

| 模块 | 说明 | 优先级 |
|------|------|--------|
| IdGenerator | 雪花算法ID生成器 | P0 |
| PushMessage | 推送消息模型 | P0 |
| PriorityPushQueue | 优先级队列 | P0 |
| UnifiedPusher | 统一推送执行层 | P0 |
| UnifiedHttpClient | 统一HTTP客户端 | P0 |
| ThirdPartyConfig | 第三方配置模型 | P0 |
| ThirdPartyConfigService | 配置管理服务 | P0 |
| TemplateEngine | 模板引擎（变量替换） | P0 |
| ResponseParser | 响应解析器（JsonPath） | P0 |
| AuthHandler | 认证处理器 | P0 |

### 14.2 接入层

| 模块 | 说明 | 优先级 |
|------|------|--------|
| PushController | API控制器 | P0 |
| PushRequest | 请求对象 | P0 |
| KafkaProducerService | Kafka生产者 | P0 |
| KafkaConsumerService | Kafka消费者 | P0 |

### 14.3 支撑层

| 模块 | 说明 | 优先级 |
|------|------|--------|
| PushRecordService | 推送记录服务 | P0 |
| PushFailureService | 失败记录服务 | P0 |
| AlertService | 告警服务 | P1 |
| MetricsCollector | 监控指标采集 | P1 |
| RetryHandler | 重试处理器 | P0 |

### 14.4 数据库表

| 表名 | 说明 | 优先级 |
|------|------|--------|
| third_party_config | 第三方配置表 | P0 |
| push_record | 推送记录表 | P0 |
| push_failure | 失败记录表 | P0 |

### 14.5 配置文件

| 文件 | 说明 | 优先级 |
|------|------|--------|
| application.yml | 应用配置 | P0 |
| pom.xml | Maven依赖 | P0 |

## 15. 开发计划

### 阶段1：核心功能（P0）
1. ID生成器
2. 消息模型和队列
3. 统一推送执行层
4. 统一HTTP客户端
5. 模板引擎和响应解析
6. 认证处理器
7. 第三方配置管理
8. 接入层API
9. Kafka生产者/消费者
10. 推送记录持久化

### 阶段2：增强功能（P1）
1. 告警服务
2. 监控指标
3. 管理后台API
4. 健康检查

### 阶段3：优化和运维（P2）
1. 性能优化
2. 监控大盘
3. 运维工具

本设计文档提供了一个完整的B2B第三方API推送系统架构，核心特点：

1. **分层解耦**：接入层、队列层、执行层、HTTP调用层清晰分离
2. **灵活接入**：支持HTTP直接、HTTP转Kafka、Kafka消费三种方式
3. **优先级保障**：三级优先级确保核心业务优先处理
4. **统一HTTP调用**：所有第三方通过统一HTTP客户端调用，配置驱动
5. **配置化管理**：
   - Header模板化：支持变量替换
   - Body模板化：灵活构建不同格式
   - 响应解析配置化：JsonPath表达式判断成功/失败
   - 认证配置化：支持API Key、签名、OAuth2.0等多种认证
6. **可靠性保障**：失败重试、告警、完整记录
7. **可观测性**：全链路追踪、监控指标、日志记录
8. **可扩展性**：水平扩展、配置化管理

系统满足B2B场景下的高可用、高性能、易维护要求，通过配置化设计屏蔽了不同第三方系统的差异，无需为每个第三方编写独立的适配器代码。
