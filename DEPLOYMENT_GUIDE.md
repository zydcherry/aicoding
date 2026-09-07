# 快速部署指南

## 前置条件
- ✅ MySQL 5.7+
- ✅ Redis 3.0+
- ✅ JDK 1.8+
- ✅ Maven 3.6+

---

## 一、数据库初始化

### 方案A：新环境（全新安装）
```bash
mysql -u root -p < src/main/resources/db/init.sql
```

### 方案B：已有环境（增量更新）
```bash
# 1. 添加event表的subscriber_count字段
mysql -u root -p push_system < src/main/resources/db/migration/001_add_subscriber_count.sql

# 2. 添加subscription表的filter_condition字段
mysql -u root -p push_system < src/main/resources/db/migration/002_add_filter_condition.sql
```

---

## 二、配置文件

### 更新 application.yml

```yaml
spring:
  # 数据库配置
  datasource:
    url: jdbc:mysql://localhost:3306/push_system?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password  # 修改为实际密码
    
  # Redis配置
  redis:
    host: localhost
    port: 6379
    password:  # 如果有密码请填写
    database: 0

push:
  cleanup:
    enabled: true                    # 启用数据清理
    event-retention-days: 30         # 事件保留30天
    notification-retention-days: 90  # 通知记录保留90天
```

---

## 三、编译打包

```bash
mvn clean package -DskipTests
```

---

## 四、启动服务

```bash
java -jar target/push-system-1.0.0-SNAPSHOT.jar
```

---

## 五、验证功能

### 5.1 检查启动日志

```
✅ 事件类型索引初始容量: 100
✅ 事件类型索引重建完成，加载了X个启用的订阅
✅ 启动统一推送执行层，工作线程数: 10
```

### 5.2 测试推送功能

```bash
# 1. 发布一个测试事件
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "ORDER_CREATED",
    "eventData": "{\"orderId\":\"12345\",\"amount\":1000}",
    "bizId": "12345",
    "sourceSystem": "order-service"
  }'

# 2. 检查数据库
mysql -u root -p push_system -e "
  SELECT * FROM event ORDER BY created_at DESC LIMIT 1;
  SELECT * FROM notification_record ORDER BY created_at DESC LIMIT 5;
"
```

### 5.3 验证数据持久化

**检查点**：
- ✅ event表有记录，status=2（已完成）
- ✅ notification_record表有记录，每个订阅者一条
- ✅ notification_record记录了请求数据、响应数据、耗时

---

## 六、健康检查

### 6.1 数据库连接
```bash
mysql -u root -p push_system -e "SELECT COUNT(*) FROM subscription;"
```

### 6.2 Redis连接
```bash
redis-cli ping
# 应该返回：PONG
```

### 6.3 推送成功率
```sql
SELECT 
  COUNT(*) as total,
  SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) as success,
  SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END) as failed,
  ROUND(SUM(CASE WHEN status = 2 THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) as success_rate
FROM notification_record
WHERE created_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR);
```

---

## 七、定时任务

系统会自动执行以下定时任务：

| 任务 | 时间 | 说明 |
|------|------|------|
| 索引重建 | 每10分钟 | 同步订阅索引 |
| 清理事件 | 每天03:00 | 删除30天前的已完成事件 |
| 清理通知 | 每天04:00 | 删除90天前的通知记录 |

**首次部署后监控**：
```bash
# 查看日志确认定时任务执行
tail -f logs/push-system.log | grep -E "定时|清理"
```

---

## 八、常见问题

### Q1: 编译失败
```
Error: 找不到符号 getResponse()
```
**解决**：检查是否使用了正确的`getRawResponse()`方法

### Q2: 数据库连接失败
```
Error: Access denied for user 'root'@'localhost'
```
**解决**：检查application.yml中的数据库密码

### Q3: Redis连接失败
```
Error: Unable to connect to Redis
```
**解决**：
```bash
# 启动Redis
redis-server

# 检查Redis状态
redis-cli ping
```

### Q4: 订阅索引为空
```
事件找到0个订阅者
```
**解决**：检查subscription表是否有启用的订阅（status=1）

### Q5: 推送记录未保存
```
notification_record表为空
```
**解决**：
1. 检查表是否存在
2. 检查NotificationRecordMapper是否被正确扫描
3. 查看日志是否有数据库错误

---

## 九、监控命令

### 查看最近推送
```sql
SELECT 
  notification_id,
  push_mode,
  status,
  cost_time,
  created_at
FROM notification_record
ORDER BY created_at DESC
LIMIT 10;
```

### 查看失败推送
```sql
SELECT 
  notification_id,
  callback_url,
  error_message,
  retry_count,
  created_at
FROM notification_record
WHERE status = 3
ORDER BY created_at DESC
LIMIT 10;
```

### 查看订阅列表
```sql
SELECT 
  id,
  subscriber_name,
  event_types,
  status,
  created_at
FROM subscription
WHERE status = 1
ORDER BY id;
```

---

## 十、性能调优

### 10.1 调整工作线程数
```yaml
push:
  system:
    worker-thread-count: 20  # 根据CPU核心数调整
```

### 10.2 调整队列容量
```yaml
push:
  system:
    queue-capacity: 20000  # 根据内存大小调整
```

### 10.3 调整Redis缓存TTL
```java
// RedisConfig.java
cacheConfigurations.put("subscription", config.entryTtl(Duration.ofMinutes(60)));
```

---

## 十一、回滚方案

如果新版本有问题，可以回滚：

### 1. 停止服务
```bash
kill -9 $(ps aux | grep push-system | grep -v grep | awk '{print $2}')
```

### 2. 恢复旧版本
```bash
java -jar target/push-system-1.0.0-SNAPSHOT.jar.bak
```

### 3. 数据库不需要回滚
新增的字段不影响旧版本运行，可以保留。

---

## 十二、文档索引

- 📘 **系统设计**：`MESSAGE_ROUTING_MECHANISM.md`
- 📗 **持久化方案**：`PERSISTENCE_IMPLEMENTATION.md`
- 📙 **问题分析**：`DESIGN_ISSUES_AND_IMPROVEMENTS.md`
- 📕 **修复总结**：`FIXES_SUMMARY.md`
- 📖 **部署指南**：本文档

---

## 支持

如有问题，请查看日志：
```bash
tail -f logs/push-system.log
```

关键日志搜索：
- 推送失败：`grep "推送失败" logs/push-system.log`
- 数据清理：`grep "清理" logs/push-system.log`
- 索引重建：`grep "索引重建" logs/push-system.log`

---

**部署完成后，系统已具备生产级可用性！** 🎉
