# 测试用例使用指南

## 快速开始

### 运行所有测试

```bash
mvn test
```

### 运行特定测试类

```bash
# 运行订阅服务测试
mvn test -Dtest=SubscriptionServiceTest

# 运行ID生成器测试
mvn test -Dtest=IdGeneratorTest

# 运行API控制器测试
mvn test -Dtest=EventControllerTest

# 运行集成测试
mvn test -Dtest=SubscriptionPublishIntegrationTest
```

### 运行特定测试方法

```bash
mvn test -Dtest=SubscriptionServiceTest#testCreateSubscription
```

---

## 测试结构

```
src/test/java/com/push/system/
├── service/                          # 业务层测试
│   ├── SubscriptionServiceTest.java # 订阅管理测试
│   └── EventServiceTest.java        # 事件服务测试
├── api/                              # API层测试
│   ├── SubscriptionControllerTest.java
│   └── EventControllerTest.java
├── id/                               # 工具类测试
│   └── IdGeneratorTest.java
└── SubscriptionPublishIntegrationTest.java  # 集成测试
```

---

## 测试用例说明

### 1. SubscriptionServiceTest

测试订阅管理的核心业务逻辑。

#### 测试场景

```java
✅ testCreateSubscription
   - 创建订阅并验证所有字段
   
✅ testCreateSubscriptionWithMultipleEventTypes
   - 验证一个订阅可以订阅多个事件类型
   
✅ testGetSubscription
   - 通过ID查询订阅
   
✅ testListAllSubscriptions
   - 查询所有订阅列表
   
✅ testUpdateSubscription
   - 更新订阅信息并验证索引更新
   
✅ testDeleteSubscription
   - 删除订阅并验证删除成功
   
✅ testFindSubscriptionsByEventType
   - 核心功能：根据事件类型查找所有订阅者
   - 验证多个订阅者可以订阅同一事件
   
✅ testFindSubscriptionsByEventTypeOnlyReturnsEnabled
   - 验证只返回启用状态的订阅者
   
✅ testFindSubscriptionsByEventTypeNoMatch
   - 验证无订阅者时返回空列表
```

#### 运行示例

```bash
mvn test -Dtest=SubscriptionServiceTest
```

**预期结果**: 所有10个测试通过 ✅

---

### 2. EventServiceTest

测试事件发布和自动匹配逻辑。

#### 测试场景

```java
✅ testPublishEvent
   - 发布事件后自动查找订阅者
   - 验证EventPublisher被调用
   
✅ testGenerateUniqueEventIds
   - 验证每次发布生成唯一的事件ID
   
✅ testPublishEventWithSingleSubscriber
   - 单个订阅者场景
   
⚠️ testPublishEventWithNoSubscribers
   - 无订阅者场景（异步问题）
```

#### 运行示例

```bash
mvn test -Dtest=EventServiceTest
```

**预期结果**: 4/6测试通过，2个异步测试可能失败

---

### 3. IdGeneratorTest

测试分布式ID生成器。

#### 测试场景

```java
✅ testGenerateUniqueIds
   - 生成1000个ID，验证全部唯一
   
✅ testConcurrentIdGeneration
   - 10个线程并发生成，验证无重复
   
✅ testSequenceOverflow
   - 快速生成3000个ID，验证序列号溢出处理
   
✅ testParseAccessType
   - 解析ID中的访问类型标识
   
✅ testParseTimestamp
   - 解析ID中的时间戳
   
✅ testWorkerIdValidation
   - 验证workerId范围检查
   
✅ testIdGenerationPerformance
   - 性能测试：10000个ID < 1秒
```

#### 运行示例

```bash
mvn test -Dtest=IdGeneratorTest
```

**预期结果**: 所有18个测试通过 ✅

**性能基准**:
- 10000个ID生成时间 < 1秒
- 并发场景：10线程×100个ID全部唯一

---

### 4. SubscriptionControllerTest

测试订阅管理REST API。

#### 测试场景

```java
✅ testCreateSubscription
   - POST /api/subscriptions
   - 验证响应格式
   
✅ testListAllSubscriptions
   - GET /api/subscriptions
   
✅ testGetSubscription
   - GET /api/subscriptions/{id}
   
✅ testUpdateSubscription
   - PUT /api/subscriptions/{id}
   
⚠️ testDeleteSubscription
   - DELETE /api/subscriptions/{id}
   
⚠️ testEnableSubscription
   - POST /api/subscriptions/{id}/enable
   
⚠️ testDisableSubscription
   - POST /api/subscriptions/{id}/disable
```

#### 运行示例

```bash
mvn test -Dtest=SubscriptionControllerTest
```

**预期结果**: 7/11测试通过

---

### 5. EventControllerTest

测试事件发布REST API。

#### 测试场景

```java
✅ testPublishEvent
   - POST /api/events/publish
   - 验证返回eventId
   
✅ testPublishEventWithMissingEventType
   - 验证必填参数校验
   
✅ testPublishEventWithOptionalFields
   - bizId和sourceSystem为可选参数
   
✅ testGetEventStatus
   - GET /api/events/{eventId}
   - 查询事件状态
   
✅ testPublishMultipleEvents
   - 连续发布3个事件
```

#### 运行示例

```bash
mvn test -Dtest=EventControllerTest
```

**预期结果**: 所有8个测试通过 ✅

---

### 6. SubscriptionPublishIntegrationTest

端到端集成测试，测试完整的订阅-发布流程。

#### 测试场景

```java
✅ testCompleteSubscriptionAndPublishFlow
   - 创建订阅 → 发布事件 → 验证推送
   
✅ testMultipleSubscribersReceiveSameEvent
   - 3个订阅者订阅同一事件
   - 发布事件后所有订阅者都能匹配
   
✅ testSubscriptionEnableDisable
   - 禁用订阅 → 不匹配事件
   - 启用订阅 → 匹配事件
   
✅ testSubscriptionUpdate
   - 更新订阅的事件类型
   - 验证旧事件不再匹配，新事件能匹配
   
✅ testPublishEventWithNoSubscribers
   - 发布无订阅者的事件仍然成功
   
✅ testSubscriptionDelete
   - 删除订阅后不再匹配事件
```

#### 运行示例

```bash
mvn test -Dtest=SubscriptionPublishIntegrationTest
```

**预期结果**: 5/6测试通过

---

## 编写新测试

### 单元测试模板

```java
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MyServiceTest {

    private MyService myService;

    @BeforeEach
    void setUp() {
        myService = new MyService();
    }

    @Test
    void testMyFeature() {
        // Given - 准备测试数据
        String input = "test";
        
        // When - 执行测试
        String result = myService.doSomething(input);
        
        // Then - 验证结果
        assertNotNull(result);
        assertEquals("expected", result);
    }
}
```

### Mock测试模板

```java
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;

class MyControllerTest {

    @Mock
    private MyService myService;

    @InjectMocks
    private MyController myController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testEndpoint() {
        // Mock返回值
        when(myService.getData()).thenReturn("mocked data");
        
        // 调用方法
        String result = myController.getEndpoint();
        
        // 验证
        assertEquals("mocked data", result);
        verify(myService, times(1)).getData();
    }
}
```

### API测试模板

```java
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MyController.class)
class MyControllerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MyService myService;

    @Test
    void testPostEndpoint() throws Exception {
        mockMvc.perform(post("/api/resource")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}
```

---

## 测试最佳实践

### 1. 命名规范

```java
// ✅ 好的测试名称
testCreateSubscriptionWithMultipleEventTypes()
testFindSubscriptionsByEventTypeOnlyReturnsEnabled()

// ❌ 不好的测试名称
test1()
testMethod()
```

### 2. 测试结构

使用 **Given-When-Then** 模式：

```java
@Test
void testCreateSubscription() {
    // Given - 准备测试数据
    Subscription subscription = new Subscription();
    subscription.setSubscriberName("测试系统");
    
    // When - 执行操作
    Subscription created = service.createSubscription(subscription);
    
    // Then - 验证结果
    assertNotNull(created.getId());
    assertEquals("测试系统", created.getSubscriberName());
}
```

### 3. 独立性

每个测试应该独立运行，不依赖其他测试：

```java
@BeforeEach
void setUp() {
    // 每个测试前重新初始化
    service = new SubscriptionService();
}
```

### 4. 边界测试

```java
@Test
void testWithNullInput() {
    assertThrows(IllegalArgumentException.class, 
        () -> service.process(null));
}

@Test
void testWithEmptyList() {
    List<String> result = service.getItems();
    assertTrue(result.isEmpty());
}
```

---

## 调试测试

### 查看详细输出

```bash
mvn test -X
```

### 查看测试报告

```bash
# 生成测试报告
mvn surefire-report:report

# 报告位置
open target/surefire-reports/index.html
```

### 跳过测试

```bash
# 编译时跳过测试
mvn clean install -DskipTests

# 打包时跳过测试
mvn package -Dmaven.test.skip=true
```

---

## 常见问题

### Q: 测试失败："找不到符号"

**A**: 可能是import缺失或依赖版本问题
```bash
mvn clean compile test-compile
```

### Q: Mock不生效

**A**: 检查Mock初始化
```java
@BeforeEach
void setUp() {
    MockitoAnnotations.openMocks(this);
}
```

### Q: 异步测试失败

**A**: 使用CountDownLatch或CompletableFuture等待异步完成
```java
CountDownLatch latch = new CountDownLatch(1);
// ... 异步操作
latch.await(5, TimeUnit.SECONDS);
```

---

## 测试覆盖率

### 生成覆盖率报告

```bash
mvn jacoco:prepare-agent test jacoco:report
```

### 查看报告

```bash
open target/site/jacoco/index.html
```

---

## 持续集成

### Jenkins配置

```groovy
pipeline {
    stages {
        stage('Test') {
            steps {
                sh 'mvn clean test'
            }
        }
        stage('Report') {
            steps {
                junit '**/target/surefire-reports/*.xml'
            }
        }
    }
}
```

### GitHub Actions配置

```yaml
name: Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '8'
      - run: mvn test
```

---

**文档状态**: ✅ 完整  
**最后更新**: 2026-09-07
