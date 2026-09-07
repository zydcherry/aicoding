package com.push.system.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.push.system.config.PushSystemProperties;
import com.push.system.entity.Subscription;
import com.push.system.mapper.SubscriptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 订阅管理服务
 */
@Service
public class SubscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PushSystemProperties pushSystemProperties;

    @Autowired
    private SubscriptionMapper subscriptionMapper;

    // 事件类型索引缓存: eventType -> List<subscriptionId>
    // 用于快速查找，仍然保留内存索引以提升性能
    private final Map<String, List<Long>> eventTypeIndex = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 根据配置初始化索引容量
        int initialCapacity = pushSystemProperties.getSubscription().getEventTypeIndexInitialCapacity();
        logger.info("事件类型索引初始容量: {}", initialCapacity);

        // 从数据库加载现有订阅并重建索引
        rebuildEventTypeIndex();

        // 根据配置决定是否加载示例数据
        if (pushSystemProperties.getSubscription().getLoadSampleData()) {
            loadSampleData();
        }
    }

    /**
     * 重建事件类型索引
     */
    private void rebuildEventTypeIndex() {
        eventTypeIndex.clear();
        List<Subscription> allSubscriptions = subscriptionMapper.selectByStatusEnabled();
        for (Subscription subscription : allSubscriptions) {
            updateEventTypeIndex(subscription);
        }
        logger.info("事件类型索引重建完成，加载了{}个启用的订阅", allSubscriptions.size());
    }

    /**
     * 创建订阅
     */
    public Subscription createSubscription(Subscription subscription) {
        // 设置时间戳
        subscription.setCreatedAt(LocalDateTime.now());
        subscription.setUpdatedAt(LocalDateTime.now());

        if (subscription.getStatus() == null) {
            subscription.setStatus(1);
        }

        // 验证
        validateSubscription(subscription);

        // 保存到数据库
        subscriptionMapper.insert(subscription);

        // 更新索引（仅启用状态）
        if (subscription.getStatus() == 1) {
            updateEventTypeIndex(subscription);
        }

        logger.info("创建订阅成功, ID: {}, 订阅者: {}, 事件类型: {}",
            subscription.getId(), subscription.getSubscriberName(), subscription.getEventTypes());

        return subscription;
    }

    /**
     * 查询订阅（使用Redis缓存）
     */
    @Cacheable(value = "subscription", key = "#id", unless = "#result == null")
    public Subscription getSubscription(Long id) {
        return subscriptionMapper.selectById(id);
    }

    /**
     * 查询所有订阅
     */
    public List<Subscription> listSubscriptions() {
        return subscriptionMapper.selectAll();
    }

    /**
     * 更新订阅
     */
    @CacheEvict(value = "subscription", key = "#id")
    public Subscription updateSubscription(Long id, Subscription subscription) {
        // 先查询旧订阅，用于更新索引
        Subscription oldSubscription = subscriptionMapper.selectById(id);
        if (oldSubscription == null) {
            throw new IllegalArgumentException("订阅不存在: " + id);
        }

        // 设置ID和更新时间
        subscription.setId(id);
        subscription.setUpdatedAt(LocalDateTime.now());
        subscription.setCreatedAt(oldSubscription.getCreatedAt());

        // 验证
        validateSubscription(subscription);

        // 从索引中移除旧的
        if (oldSubscription.getStatus() == 1) {
            removeFromEventTypeIndex(oldSubscription);
        }

        // 更新数据库
        subscriptionMapper.update(subscription);

        // 添加新的到索引（仅启用状态）
        if (subscription.getStatus() == 1) {
            updateEventTypeIndex(subscription);
        }

        logger.info("更新订阅成功, ID: {}", id);

        return subscription;
    }

    /**
     * 根据事件类型查找订阅者
     */
    public List<Subscription> findSubscriptionsByEventType(String eventType) {
        // 先从缓存索引查找订阅ID
        List<Long> subscriptionIds = eventTypeIndex.get(eventType);
        if (subscriptionIds == null || subscriptionIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 从数据库加载完整订阅信息
        return subscriptionIds.stream()
            .map(subscriptionMapper::selectById)
            .filter(Objects::nonNull)
            .filter(s -> s.getStatus() == 1)  // 再次确认状态
            .collect(Collectors.toList());
    }

    /**
     * 删除订阅
     */
    @CacheEvict(value = "subscription", key = "#id")
    public void deleteSubscription(Long id) {
        Subscription subscription = subscriptionMapper.selectById(id);
        if (subscription != null) {
            // 从索引中移除
            removeFromEventTypeIndex(subscription);

            // 从数据库删除
            subscriptionMapper.deleteById(id);

            logger.info("删除订阅成功, ID: {}", id);
        }
    }

    /**
     * 验证订阅
     */
    private void validateSubscription(Subscription subscription) {
        if (subscription.getSubscriberName() == null || subscription.getSubscriberName().isEmpty()) {
            throw new IllegalArgumentException("订阅者名称不能为空");
        }
        if (subscription.getCallbackUrl() == null || subscription.getCallbackUrl().isEmpty()) {
            throw new IllegalArgumentException("回调URL不能为空");
        }
        if (!subscription.getCallbackUrl().startsWith("http://") &&
            !subscription.getCallbackUrl().startsWith("https://")) {
            throw new IllegalArgumentException("回调URL格式错误");
        }
        if (subscription.getEventTypes() == null || subscription.getEventTypes().isEmpty()) {
            throw new IllegalArgumentException("订阅事件类型不能为空");
        }
    }

    /**
     * 更新事件类型索引
     */
    private void updateEventTypeIndex(Subscription subscription) {
        for (String eventType : subscription.getEventTypes()) {
            eventTypeIndex.computeIfAbsent(eventType, k -> new ArrayList<>()).add(subscription.getId());
        }
    }

    /**
     * 从索引中移除订阅
     */
    private void removeFromEventTypeIndex(Subscription subscription) {
        for (String eventType : subscription.getEventTypes()) {
            List<Long> ids = eventTypeIndex.get(eventType);
            if (ids != null) {
                ids.remove(subscription.getId());
            }
        }
    }

    /**
     * 加载示例数据
     */
    private void loadSampleData() {
        try {
            // 检查数据库中是否已有数据
            List<Subscription> existing = subscriptionMapper.selectAll();
            if (!existing.isEmpty()) {
                logger.info("数据库中已存在{}个订阅，跳过示例数据加载", existing.size());
                return;
            }

            // CRM系统订阅
            Subscription sub1 = new Subscription();
            sub1.setSubscriberName("CRM系统");
            sub1.setCallbackUrl("https://crm.example.com/webhook");
            sub1.setEventTypes(Arrays.asList("USER_REGISTERED", "ORDER_CREATED"));
            sub1.setDescription("CRM系统订阅用户和订单事件");
            createSubscription(sub1);

            // 物流系统订阅
            Subscription sub2 = new Subscription();
            sub2.setSubscriberName("物流系统");
            sub2.setCallbackUrl("https://logistics.example.com/webhook");
            sub2.setEventTypes(Arrays.asList("ORDER_CREATED", "ORDER_SHIPPED"));
            sub2.setDescription("物流系统订阅订单事件");
            createSubscription(sub2);

            // 财务系统订阅
            Subscription sub3 = new Subscription();
            sub3.setSubscriberName("财务系统");
            sub3.setCallbackUrl("https://finance.example.com/webhook");
            sub3.setEventTypes(Arrays.asList("ORDER_PAID"));
            sub3.setDescription("财务系统订阅支付事件");
            createSubscription(sub3);

            logger.info("加载示例订阅数据完成");

        } catch (Exception e) {
            logger.error("加载示例订阅数据失败", e);
        }
    }
}
