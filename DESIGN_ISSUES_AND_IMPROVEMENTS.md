# 系统设计问题与优化建议

## 检查日期
2026-09-07

---

## 🔴 严重问题

### 1. **推送结果没有持久化记录** ⚠️

**问题描述**：
- 数据库中有`notification_record`表和`push_failure`表的设计
- 但是代码中**完全没有实现**对这两张表的写入
- 推送成功/失败的记录只在日志中，没有持久化

**影响**：
- ❌ 无法追踪推送历史
- ❌ 无法统计推送成功率
- ❌ 重启后失败重试信息丢失
- ❌ 无法提供推送状态查询API
- ❌ 无法进行数据分析和监控

**位置**：
- `UnifiedPusher.java:305-315` - 推送成功/失败只记录日志
- `UnifiedPusher.java:378` - TODO注释：记录到失败表，触发告警

**建议修复**：
```java
// 1. 创建NotificationRecordMapper
@Mapper
public interface NotificationRecordMapper {
    @Insert("INSERT INTO notification_record (...) VALUES (...)")
    int insert(NotificationRecord record);
    
    @Update("UPDATE notification_record SET status=#{status}, ... WHERE notification_id=#{notificationId}")
    int updateStatus(NotificationRecord record);
}

// 2. 在UnifiedPusher中注入并使用
// 推送前：创建记录（状态=待推送）
// 推送后：更新记录（状态=成功/失败，记录响应数据、耗时等）
```

---

### 2. **Subscription实体类设计不合理** ⚠️

**问题描述**：
```java
public class Subscription {
    private List<String> eventTypes;    // 业务字段
    private String eventTypesJson;      // 数据库字段
    
    // setter中自动转换
    public void setEventTypes(List<String> eventTypes) {
        this.eventTypes = eventTypes;
        this.eventTypesJson = objectMapper.writeValueAsString(eventTypes);  // ❌
    }
}
```

**问题**：
1. **静态ObjectMapper线程不安全**：`private static final ObjectMapper objectMapper`
2. **双字段冗余**：eventTypes和eventTypesJson需要手动同步
3. **JSON转换异常处理不当**：抛出RuntimeException
4. **MyBatis结果映射复杂**：需要AS别名映射

**影响**：
- 潜在的线程安全问题
- 容易出现两个字段不一致的bug
- 代码维护困难

**建议修复方案**：

**方案1：使用MyBatis TypeHandler（推荐）**
```java
// 1. 创建TypeHandler
@MappedTypes(List.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JsonListTypeHandler extends BaseTypeHandler<List<String>> {
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, mapper.writeValueAsString(parameter));
    }
    
    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String json = rs.getString(columnName);
        return json == null ? null : mapper.readValue(json, new TypeReference<List<String>>() {});
    }
    // ... 其他方法
}

// 2. 简化实体类
public class Subscription {
    private Long id;
    private String subscriberName;
    private String callbackUrl;
    private List<String> eventTypes;  // 只保留一个字段
    // ... 其他字段，去掉eventTypesJson
}

// 3. Mapper中使用TypeHandler
@Select("SELECT * FROM subscription WHERE id = #{id}")
@Results({
    @Result(property = "eventTypes", column = "event_types", 
            typeHandler = JsonListTypeHandler.class)
})
Subscription selectById(Long id);
```

**方案2：分离JSON转换逻辑**
```java
// 创建转换工具类
@Component
public class SubscriptionConverter {
    @Autowired
    private ObjectMapper objectMapper;
    
    public Subscription fromDb(SubscriptionDO dbEntity) {
        // 转换逻辑
    }
    
    public SubscriptionDO toDb(Subscription entity) {
        // 转换逻辑
    }
}
```

---

### 3. **内存索引重建机制不完善** ⚠️

**问题描述**：
```java
@PostConstruct
public void init() {
    rebuildEventTypeIndex();  // 只在启动时重建一次
}

public Subscription updateSubscription(Long id, Subscription subscription) {
    // 更新数据库
    subscriptionMapper.update(subscription);
    
    // 更新内存索引
    if (subscription.getStatus() == 1) {
        updateEventTypeIndex(subscription);
    }
}
```

**问题**：
1. **缓存失效后索引不更新**：Redis缓存过期后，从数据库查询的订阅不会更新内存索引
2. **多实例不同步**：集群环境下，实例A更新订阅，实例B的索引不会更新
3. **索引可能遗漏**：如果更新失败但索引已更新，导致不一致

**影响**：
- 集群环境下事件路由可能不准确
- 内存索引与数据库数据不一致

**建议修复**：

**方案1：定时重建索引**
```java
@Scheduled(fixedRate = 60000)  // 每分钟重建
public void refreshEventTypeIndex() {
    rebuildEventTypeIndex();
}
```

**方案2：使用Redis Pub/Sub同步**
```java
// 实例A更新订阅后，发布消息
redisTemplate.convertAndSend("subscription:update", subscriptionId);

// 所有实例监听消息，刷新索引
@RedisMessageListener
public void onSubscriptionUpdate(Long subscriptionId) {
    Subscription sub = subscriptionMapper.selectById(subscriptionId);
    rebuildEventTypeIndex();
}
```

**方案3：使用数据库触发器 + 消息队列**

---

## 🟡 中等问题

### 4. **事件状态更新机制缺失**

**问题描述**：
```java
// EventService中发布事件后，状态设置为"处理中"
event.setStatus(1);  // 处理中
eventMapper.insert(event);

// 但是没有代码在推送完成后更新状态为"已完成"
```

**影响**：
- 事件状态永远是"处理中"
- 无法判断事件是否处理完成
- status字段失去意义

**建议修复**：
```java
// EventPublisher中推送完成后通知
public void publishToSubscribers(Event event, List<Subscription> subscriptions) {
    CountDownLatch latch = new CountDownLatch(subscriptions.size());
    
    for (Subscription subscription : subscriptions) {
        CompletableFuture.runAsync(() -> {
            try {
                // 推送逻辑
            } finally {
                latch.countDown();
            }
        });
    }
    
    // 等待所有推送完成
    latch.await(30, TimeUnit.SECONDS);
    
    // 更新事件状态为已完成
    eventService.updateEventStatus(event.getEventId(), 2);
}
```

---

### 5. **缺少推送幂等性保证**

**问题描述**：
- 推送失败重试时，可能重复推送
- 没有幂等性标识
- 接收方可能收到重复消息

**建议修复**：
```java
// 1. 在推送消息中加入幂等性ID
Map<String, Object> content = new HashMap<>();
content.put("idempotencyKey", generateIdempotencyKey(event, subscription));
content.put("eventId", event.getEventId());

// 2. 在notification_record表中记录
// 重试前先查询是否已成功推送过
```

---

### 6. **事件数据缺少清理机制**

**问题描述**：
- event表数据会无限增长
- notification_record表数据会无限增长
- 没有归档和清理策略

**影响**：
- 数据库空间占用越来越大
- 查询性能下降
- 索引效率降低

**建议修复**：
```java
// 1. 定时任务清理历史数据
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨2点
public void cleanOldEvents() {
    // 删除30天前的已完成事件
    eventMapper.deleteByStatusAndCreatedBefore(2, LocalDateTime.now().minusDays(30));
}

// 2. 或者使用MySQL分区表
CREATE TABLE event (
    ...
) PARTITION BY RANGE (TO_DAYS(created_at)) (
    PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
    PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
    ...
);
```

---

### 7. **订阅过滤条件不够灵活**

**问题描述**：
- 当前只能按事件类型订阅
- 无法实现更细粒度的订阅过滤

**示例场景**：
```
需求：物流系统只想订阅"华东地区"的ORDER_CREATED事件
当前：只能订阅所有ORDER_CREATED事件
```

**建议优化**：
```sql
-- 扩展subscription表
ALTER TABLE subscription ADD COLUMN filter_condition JSON COMMENT '过滤条件';

-- 示例
{
    "region": ["east", "south"],
    "amount": {"$gt": 1000},
    "bizType": "B2B"
}

-- 代码中使用JsonPath过滤
public List<Subscription> findSubscriptionsByEventType(String eventType, Map<String, Object> eventData) {
    List<Subscription> all = subscriptionMapper.selectByEventType(eventType);
    return all.stream()
        .filter(sub -> matchesFilter(sub.getFilterCondition(), eventData))
        .collect(Collectors.toList());
}
```

---

## 🟢 轻微问题与优化建议

### 8. **日志级别使用不当**

**问题**：
```java
logger.info("开始推送事件给订阅者, eventId: {}, 订阅者数量: {}", ...);  // 正常
logger.warn("事件没有订阅者, eventId: {}, eventType: {}", ...);  // ❌ 应该是info
```

**建议**：
- warn：实际问题（推送失败、配置缺失）
- info：正常业务流程
- debug：详细调试信息

---

### 9. **缺少监控指标暴露**

**建议添加**：
```java
@Component
public class PushMetrics {
    private final MeterRegistry registry;
    
    public void recordPushSuccess(String eventType) {
        Counter.builder("push.success")
            .tag("event_type", eventType)
            .register(registry)
            .increment();
    }
    
    public void recordPushLatency(long millis) {
        Timer.builder("push.latency")
            .register(registry)
            .record(millis, TimeUnit.MILLISECONDS);
    }
}
```

---

### 10. **批量操作性能优化**

**问题**：
```java
// SubscriptionService重建索引
for (Subscription subscription : allSubscriptions) {
    updateEventTypeIndex(subscription);  // 逐个处理
}
```

**优化**：
```java
// 使用流式处理
allSubscriptions.parallelStream()
    .forEach(this::updateEventTypeIndex);
```

---

### 11. **配置参数可以更丰富**

**建议添加**：
```yaml
push:
  subscription:
    # 索引重建策略
    index-rebuild-strategy: startup  # startup / scheduled / manual
    index-rebuild-cron: "0 */10 * * * ?"  # 10分钟重建一次
    
  # 推送重试配置
  retry:
    max-attempts: 3
    backoff-multiplier: 2
    max-backoff-seconds: 60
    
  # 数据清理配置
  cleanup:
    enabled: true
    event-retention-days: 30
    notification-retention-days: 90
```

---

### 12. **缺少健康检查接口**

**建议添加**：
```java
@RestController
@RequestMapping("/actuator")
public class HealthController {
    
    @GetMapping("/health/pusher")
    public Map<String, Object> checkPusherHealth() {
        return Map.of(
            "status", "UP",
            "queueSize", pushQueue.size(),
            "workerThreads", workerPool.getActiveCount(),
            "subscriptionCount", subscriptionService.count()
        );
    }
}
```

---

## 优先级排序

### P0（必须修复）
1. ✅ 推送结果持久化（notification_record表）
2. ✅ Subscription实体类设计优化

### P1（高优先级）
3. 内存索引同步机制
4. 事件状态更新机制
5. 推送幂等性保证

### P2（中优先级）
6. 数据清理机制
7. 订阅过滤条件优化
8. 监控指标暴露

### P3（低优先级）
9. 日志级别优化
10. 批量操作优化
11. 配置参数丰富
12. 健康检查接口

---

## 总结

当前系统的**核心功能**（持久化、缓存、消息路由）基本实现，但在**生产可用性**方面还有不足：

**已完成** ✅：
- 数据库持久化（订阅、事件、配置）
- Redis缓存集成
- 三层缓存架构
- 两种推送模式

**需要补充** ⚠️：
- 推送记录持久化（最重要）
- 集群同步机制
- 数据清理策略
- 监控和告警

建议按优先级逐步完善，先解决P0和P1的问题，确保系统可以在生产环境稳定运行。
