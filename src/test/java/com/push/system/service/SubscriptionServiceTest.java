package com.push.system.service;

import com.push.system.entity.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订阅服务测试
 */
@SpringBootTest
class SubscriptionServiceTest {

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService();
    }

    @Test
    void testCreateSubscription() {
        // 准备测试数据
        Subscription subscription = new Subscription();
        subscription.setSubscriberName("测试系统");
        subscription.setCallbackUrl("https://test.example.com/webhook");
        subscription.setEventTypes(Arrays.asList("TEST_EVENT"));
        subscription.setStatus(1);
        subscription.setDescription("测试订阅");

        // 执行创建
        Subscription created = subscriptionService.createSubscription(subscription);

        // 验证结果
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("测试系统", created.getSubscriberName());
        assertEquals("https://test.example.com/webhook", created.getCallbackUrl());
        assertEquals(1, created.getEventTypes().size());
        assertEquals("TEST_EVENT", created.getEventTypes().get(0));
        assertEquals(1, created.getStatus());
        assertNotNull(created.getCreatedAt());
        assertNotNull(created.getUpdatedAt());
    }

    @Test
    void testCreateSubscriptionWithMultipleEventTypes() {
        // 测试多个事件类型
        Subscription subscription = new Subscription();
        subscription.setSubscriberName("多事件订阅系统");
        subscription.setCallbackUrl("https://multi.example.com/webhook");
        subscription.setEventTypes(Arrays.asList("EVENT_1", "EVENT_2", "EVENT_3"));
        subscription.setStatus(1);

        Subscription created = subscriptionService.createSubscription(subscription);

        assertNotNull(created);
        assertEquals(3, created.getEventTypes().size());
        assertTrue(created.getEventTypes().contains("EVENT_1"));
        assertTrue(created.getEventTypes().contains("EVENT_2"));
        assertTrue(created.getEventTypes().contains("EVENT_3"));
    }

    @Test
    void testGetSubscription() {
        // 创建订阅
        Subscription subscription = new Subscription();
        subscription.setSubscriberName("查询测试");
        subscription.setCallbackUrl("https://query.example.com/webhook");
        subscription.setEventTypes(Arrays.asList("QUERY_EVENT"));
        subscription.setStatus(1);

        Subscription created = subscriptionService.createSubscription(subscription);
        Long id = created.getId();

        // 查询订阅
        Subscription found = subscriptionService.getSubscription(id);

        assertNotNull(found);
        assertEquals(id, found.getId());
        assertEquals("查询测试", found.getSubscriberName());
    }

    @Test
    void testGetSubscriptionNotFound() {
        // 查询不存在的订阅
        Subscription found = subscriptionService.getSubscription(99999L);
        assertNull(found);
    }

    @Test
    void testListAllSubscriptions() {
        // 清空并创建多个订阅
        subscriptionService = new SubscriptionService();

        Subscription sub1 = new Subscription();
        sub1.setSubscriberName("系统1");
        sub1.setCallbackUrl("https://sys1.example.com/webhook");
        sub1.setEventTypes(Arrays.asList("EVENT_A"));
        sub1.setStatus(1);
        subscriptionService.createSubscription(sub1);

        Subscription sub2 = new Subscription();
        sub2.setSubscriberName("系统2");
        sub2.setCallbackUrl("https://sys2.example.com/webhook");
        sub2.setEventTypes(Arrays.asList("EVENT_B"));
        sub2.setStatus(1);
        subscriptionService.createSubscription(sub2);

        // 查询所有订阅
        List<Subscription> all = subscriptionService.listSubscriptions();

        assertNotNull(all);
        assertTrue(all.size() >= 2);
    }

    @Test
    void testUpdateSubscription() {
        // 创建订阅
        Subscription subscription = new Subscription();
        subscription.setSubscriberName("更新测试");
        subscription.setCallbackUrl("https://update.example.com/webhook");
        subscription.setEventTypes(Arrays.asList("OLD_EVENT"));
        subscription.setStatus(1);

        Subscription created = subscriptionService.createSubscription(subscription);
        Long id = created.getId();

        // 更新订阅
        Subscription toUpdate = new Subscription();
        toUpdate.setSubscriberName("更新后的名称");
        toUpdate.setCallbackUrl("https://updated.example.com/webhook");
        toUpdate.setEventTypes(Arrays.asList("NEW_EVENT_1", "NEW_EVENT_2"));
        toUpdate.setStatus(1);

        Subscription updated = subscriptionService.updateSubscription(id, toUpdate);

        assertNotNull(updated);
        assertEquals(id, updated.getId());
        assertEquals("更新后的名称", updated.getSubscriberName());
        assertEquals("https://updated.example.com/webhook", updated.getCallbackUrl());
        assertEquals(2, updated.getEventTypes().size());
    }

    @Test
    void testDeleteSubscription() {
        // 创建订阅
        Subscription subscription = new Subscription();
        subscription.setSubscriberName("删除测试");
        subscription.setCallbackUrl("https://delete.example.com/webhook");
        subscription.setEventTypes(Arrays.asList("DELETE_EVENT"));
        subscription.setStatus(1);

        Subscription created = subscriptionService.createSubscription(subscription);
        Long id = created.getId();

        // 删除订阅
        subscriptionService.deleteSubscription(id);

        // 验证已删除
        Subscription found = subscriptionService.getSubscription(id);
        assertNull(found);
    }

    @Test
    void testFindSubscriptionsByEventType() {
        // 创建多个订阅
        subscriptionService = new SubscriptionService();

        Subscription sub1 = new Subscription();
        sub1.setSubscriberName("订阅者1");
        sub1.setCallbackUrl("https://sub1.example.com/webhook");
        sub1.setEventTypes(Arrays.asList("ORDER_CREATED", "USER_REGISTERED"));
        sub1.setStatus(1);
        subscriptionService.createSubscription(sub1);

        Subscription sub2 = new Subscription();
        sub2.setSubscriberName("订阅者2");
        sub2.setCallbackUrl("https://sub2.example.com/webhook");
        sub2.setEventTypes(Arrays.asList("ORDER_CREATED"));
        sub2.setStatus(1);
        subscriptionService.createSubscription(sub2);

        Subscription sub3 = new Subscription();
        sub3.setSubscriberName("订阅者3");
        sub3.setCallbackUrl("https://sub3.example.com/webhook");
        sub3.setEventTypes(Arrays.asList("ORDER_PAID"));
        sub3.setStatus(1);
        subscriptionService.createSubscription(sub3);

        // 查找订阅了ORDER_CREATED的订阅者
        List<Subscription> subscribers = subscriptionService.findSubscriptionsByEventType("ORDER_CREATED");

        assertNotNull(subscribers);
        assertEquals(2, subscribers.size());
        assertTrue(subscribers.stream().anyMatch(s -> s.getSubscriberName().equals("订阅者1")));
        assertTrue(subscribers.stream().anyMatch(s -> s.getSubscriberName().equals("订阅者2")));
    }

    @Test
    void testFindSubscriptionsByEventTypeOnlyReturnsEnabled() {
        // 创建启用和禁用的订阅
        subscriptionService = new SubscriptionService();

        Subscription enabled = new Subscription();
        enabled.setSubscriberName("启用的订阅者");
        enabled.setCallbackUrl("https://enabled.example.com/webhook");
        enabled.setEventTypes(Arrays.asList("TEST_EVENT"));
        enabled.setStatus(1);
        subscriptionService.createSubscription(enabled);

        Subscription disabled = new Subscription();
        disabled.setSubscriberName("禁用的订阅者");
        disabled.setCallbackUrl("https://disabled.example.com/webhook");
        disabled.setEventTypes(Arrays.asList("TEST_EVENT"));
        disabled.setStatus(0);
        subscriptionService.createSubscription(disabled);

        // 查找订阅者，应该只返回启用的
        List<Subscription> subscribers = subscriptionService.findSubscriptionsByEventType("TEST_EVENT");

        assertNotNull(subscribers);
        assertEquals(1, subscribers.size());
        assertEquals("启用的订阅者", subscribers.get(0).getSubscriberName());
    }

    @Test
    void testFindSubscriptionsByEventTypeNoMatch() {
        // 查找不存在的事件类型
        List<Subscription> subscribers = subscriptionService.findSubscriptionsByEventType("NON_EXISTENT_EVENT");

        assertNotNull(subscribers);
        assertTrue(subscribers.isEmpty());
    }
}
