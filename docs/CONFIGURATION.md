# 配置说明文档

## 概述

推送系统支持通过 `application.yml` 配置文件进行灵活配置。所有配置都通过 `PushSystemProperties` 类统一管理。

---

## 配置结构

```yaml
push:
  system:      # 系统配置
  kafka:       # Kafka配置
  http:        # HTTP客户端配置
  subscription: # 订阅配置
```

---

## 详细配置项

### 1. 系统配置 (push.system)

控制推送系统的核心参数。

```yaml
push:
  system:
    # 队列容量（消息缓冲区大小）
    queue-capacity: 10000
    
    # 工作线程数（并发处理推送的线程数）
    worker-thread-count: 10
    
    # 轮询超时时间（秒，工作线程从队列取消息的等待时间）
    poll-timeout-seconds: 3
    
    # 默认最大重试次数（推送失败后的重试次数）
    default-max-retry: 3
```

**参数说明**:
- `queue-capacity`: 内存队列容量，建议根据系统负载调整（1000-50000）
- `worker-thread-count`: 推送并发度，建议设置为 CPU 核心数的 2-4 倍
- `poll-timeout-seconds`: 影响系统停机时的响应速度
- `default-max-retry`: 全局默认重试次数，单个消息可覆盖此配置

---

### 2. Kafka配置 (push.kafka.topics)

定义系统使用的 Kafka Topic 名称。

```yaml
push:
  kafka:
    topics:
      # 内部推送消息topic（API发送到Kafka）
      push-message: push-message-topic
      
      # 外部推送消息topic（直接消费Kafka）
      external-push: external-push-topic
      
      # 内部高优先级消息topic
      push-high-priority: push-high-priority
      
      # 内部普通优先级消息topic
      push-normal-priority: push-normal-priority
      
      # 内部低优先级消息topic
      push-low-priority: push-low-priority
```

**Topic说明**:
- `push-message`: 方式1和方式2的消息入口（未使用，预留）
- `external-push`: 方式3外部系统直接发送到Kafka的消息
- `push-high-priority`: 高优先级推送消息（紧急通知）
- `push-normal-priority`: 普通优先级推送消息（常规通知）
- `push-low-priority`: 低优先级推送消息（营销消息）

**注意**: 修改Topic名称后需要在Kafka中预先创建对应的Topic。

---

### 3. HTTP客户端配置 (push.http)

控制HTTP推送的超时和重试行为。

```yaml
push:
  http:
    # 默认连接超时（毫秒）
    default-connect-timeout: 3000
    
    # 默认读取超时（毫秒）
    default-read-timeout: 5000
    
    # 默认最大重试次数
    default-max-retry: 3
```

**参数说明**:
- `default-connect-timeout`: 建立连接的超时时间，建议 2000-5000ms
- `default-read-timeout`: 等待响应的超时时间，建议 3000-10000ms
- `default-max-retry`: HTTP调用失败时的重试次数

**性能建议**:
- 快速响应的系统可以设置较短的超时时间
- 外部API响应较慢时应适当增加 `read-timeout`
- 超时时间过长可能导致线程资源耗尽

---

### 4. 订阅配置 (push.subscription)

控制订阅管理服务的行为。

```yaml
push:
  subscription:
    # 是否启动时加载示例数据
    load-sample-data: true
    
    # 事件类型索引初始容量
    event-type-index-initial-capacity: 100
```

**参数说明**:
- `load-sample-data`: 
  - `true`: 启动时自动创建3个示例订阅（CRM、物流、财务系统）
  - `false`: 不加载示例数据（生产环境建议设置为false）
  
- `event-type-index-initial-capacity`: 
  - 事件类型索引的初始容量
  - 建议设置为预期事件类型数量的1.5倍
  - 过小会导致频繁扩容，过大浪费内存

---

## 环境配置

### 开发环境 (application-dev.yml)

```yaml
push:
  system:
    queue-capacity: 5000
    worker-thread-count: 5
    default-max-retry: 2
  
  http:
    default-connect-timeout: 2000
    default-read-timeout: 3000
  
  subscription:
    load-sample-data: true  # 开发环境加载示例数据
```

### 生产环境 (application-prod.yml)

```yaml
push:
  system:
    queue-capacity: 20000
    worker-thread-count: 20
    default-max-retry: 5
  
  http:
    default-connect-timeout: 5000
    default-read-timeout: 10000
  
  subscription:
    load-sample-data: false  # 生产环境不加载示例数据
```

---

## 配置类使用

### 在代码中注入配置

```java
@Service
public class MyService {
    
    @Autowired
    private PushSystemProperties pushSystemProperties;
    
    public void doSomething() {
        // 获取系统配置
        int queueCapacity = pushSystemProperties.getSystem().getQueueCapacity();
        
        // 获取Kafka Topic配置
        String topic = pushSystemProperties.getKafka().getTopics().getPushMessage();
        
        // 获取HTTP配置
        int timeout = pushSystemProperties.getHttp().getDefaultConnectTimeout();
        
        // 获取订阅配置
        boolean loadData = pushSystemProperties.getSubscription().getLoadSampleData();
    }
}
```

---

## 配置优先级

Spring Boot配置加载优先级（从高到低）：

1. 命令行参数: `--push.system.queue-capacity=15000`
2. 环境变量: `PUSH_SYSTEM_QUEUE_CAPACITY=15000`
3. application-{profile}.yml
4. application.yml
5. 代码中的默认值

---

## 动态配置

### 使用@RefreshScope实现配置热更新

```java
@Service
@RefreshScope  // 支持配置刷新
public class MyService {
    @Autowired
    private PushSystemProperties pushSystemProperties;
}
```

配合Spring Cloud Config或Nacos可实现配置的动态刷新。

---

## 配置验证

### 启动时验证配置

```java
@Component
public class ConfigValidator implements ApplicationListener<ContextRefreshedEvent> {
    
    @Autowired
    private PushSystemProperties properties;
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 验证队列容量
        if (properties.getSystem().getQueueCapacity() < 100) {
            throw new IllegalStateException("queue-capacity 不能小于100");
        }
        
        // 验证线程数
        if (properties.getSystem().getWorkerThreadCount() < 1) {
            throw new IllegalStateException("worker-thread-count 必须大于0");
        }
    }
}
```

---

## 性能调优建议

### 高并发场景

```yaml
push:
  system:
    queue-capacity: 50000      # 增大队列容量
    worker-thread-count: 30    # 增加工作线程
    poll-timeout-seconds: 1    # 减少轮询等待
```

### 低延迟场景

```yaml
push:
  http:
    default-connect-timeout: 1000  # 缩短超时时间
    default-read-timeout: 2000
    default-max-retry: 1           # 减少重试次数
```

### 资源受限场景

```yaml
push:
  system:
    queue-capacity: 2000       # 减小队列容量
    worker-thread-count: 5     # 减少线程数
```

---

## 监控配置

建议监控以下配置相关的指标：

1. **队列使用率**: `pushQueue.size() / queueCapacity`
2. **工作线程活跃度**: 活跃线程数 / 总线程数
3. **HTTP超时率**: 超时请求数 / 总请求数
4. **重试率**: 重试次数 / 总推送次数

---

## 常见问题

### Q: 修改配置后不生效？

**A**: 
1. 检查是否重启应用
2. 确认使用了正确的profile（如 `spring.profiles.active=dev`）
3. 检查是否有环境变量覆盖了配置

### Q: 队列容量设置多大合适？

**A**: 
- 根据公式计算: `队列容量 = 峰值QPS × 平均处理时间(秒)`
- 例: 1000 QPS × 10秒 = 10000
- 建议预留20-50%的缓冲空间

### Q: 工作线程数设置多少？

**A**:
- CPU密集型任务: `CPU核心数 + 1`
- IO密集型任务（推送系统属于此类）: `CPU核心数 × 2-4`
- 需要结合实际测试调整

---

## 配置文件示例

完整的 `application.yml` 配置示例：

```yaml
spring:
  application:
    name: push-system
  
  datasource:
    url: jdbc:mysql://localhost:3306/push_system
    username: root
    password: your_password
  
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
      retries: 3
    consumer:
      group-id: push-consumer-group

server:
  port: 8080

# 推送系统配置
push:
  system:
    queue-capacity: 10000
    worker-thread-count: 10
    poll-timeout-seconds: 3
    default-max-retry: 3
  
  kafka:
    topics:
      push-message: push-message-topic
      external-push: external-push-topic
      push-high-priority: push-high-priority
      push-normal-priority: push-normal-priority
      push-low-priority: push-low-priority
  
  http:
    default-connect-timeout: 3000
    default-read-timeout: 5000
    default-max-retry: 3
  
  subscription:
    load-sample-data: true
    event-type-index-initial-capacity: 100
```

---

**文档版本**: 1.0  
**最后更新**: 2026-09-07  
**维护者**: Push System Team
