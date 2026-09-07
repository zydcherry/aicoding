package com.push.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.push.system.entity.Subscription;
import com.push.system.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 订阅-发布模式集成测试
 * 测试完整的事件发布流程
 */
@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionPublishIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SubscriptionService subscriptionService;

    @Test
    void testCompleteSubscriptionAndPublishFlow() throws Exception {
        // 步骤1：创建订阅
        String subscriptionJson = "{" +
            "\"subscriberName\":\"集成测试系统\"," +
            "\"callbackUrl\":\"https://integration-test.example.com/webhook\"," +
            "\"eventTypes\":[\"INTEGRATION_TEST_EVENT\"]," +
            "\"status\":1" +
        "}";

        MvcResult createResult = mockMvc.perform(post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriptionJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.subscriberName").value("集成测试系统"))
            .andReturn();

        // 步骤2：查询订阅列表，验证订阅已创建
        mockMvc.perform(get("/api/subscriptions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(greaterThan(0))));

        // 步骤3：发布事件
        String eventJson = "{" +
            "\"eventType\":\"INTEGRATION_TEST_EVENT\"," +
            "\"data\":\"{\\\"testId\\\":\\\"IT001\\\",\\\"message\\\":\\\"Integration test\\\"}\"," +
            "\"bizId\":\"integration-test-001\"," +
            "\"sourceSystem\":\"TestSystem\"" +
        "}";

        MvcResult publishResult = mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.eventId").exists())
            .andExpect(jsonPath("$.message").value("事件发布成功，正在推送给订阅者"))
            .andReturn();

        String publishResponse = publishResult.getResponse().getContentAsString();
        assertTrue(publishResponse.contains("evt_"));

        // 步骤4：查询事件状态
        String eventId = objectMapper.readTree(publishResponse).get("eventId").asText();
        mockMvc.perform(get("/api/events/" + eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.eventId").value(eventId))
            .andExpect(jsonPath("$.data.eventType").value("INTEGRATION_TEST_EVENT"));
    }

    @Test
    void testMultipleSubscribersReceiveSameEvent() throws Exception {
        // 创建多个订阅者
        String sub1Json = "{" +
            "\"subscriberName\":\"订阅者1\"," +
            "\"callbackUrl\":\"https://subscriber1.example.com/webhook\"," +
            "\"eventTypes\":[\"MULTI_SUB_TEST_EVENT\"]," +
            "\"status\":1" +
        "}";

        String sub2Json = "{" +
            "\"subscriberName\":\"订阅者2\"," +
            "\"callbackUrl\":\"https://subscriber2.example.com/webhook\"," +
            "\"eventTypes\":[\"MULTI_SUB_TEST_EVENT\"]," +
            "\"status\":1" +
        "}";

        String sub3Json = "{" +
            "\"subscriberName\":\"订阅者3\"," +
            "\"callbackUrl\":\"https://subscriber3.example.com/webhook\"," +
            "\"eventTypes\":[\"MULTI_SUB_TEST_EVENT\"]," +
            "\"status\":1" +
        "}";

        mockMvc.perform(post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sub1Json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sub2Json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sub3Json))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // 发布事件
        String eventJson = "{" +
            "\"eventType\":\"MULTI_SUB_TEST_EVENT\"," +
            "\"data\":\"{\\\"message\\\":\\\"Test for multiple subscribers\\\"}\"," +
            "\"bizId\":\"multi-sub-test\"" +
        "}";

        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.eventId").exists());

        // 验证所有订阅者都能匹配到
        List<Subscription> subscribers = subscriptionService.findSubscriptionsByEventType("MULTI_SUB_TEST_EVENT");
        assertEquals(3, subscribers.size());
    }

    @Test
    void testSubscriptionEnableDisable() throws Exception {
        // 创建订阅
        String subscriptionJson = "{" +
            "\"subscriberName\":\"启用禁用测试\"," +
            "\"callbackUrl\":\"https://enable-disable-test.example.com/webhook\"," +
            "\"eventTypes\":[\"ENABLE_DISABLE_TEST\"]," +
            "\"status\":1" +
        "}";

        MvcResult createResult = mockMvc.perform(post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriptionJson))
            .andExpect(status().isOk())
            .andReturn();

        String response = createResult.getResponse().getContentAsString();
        Long subId = objectMapper.readTree(response).get("data").get("id").asLong();

        // 禁用订阅
        mockMvc.perform(post("/api/subscriptions/" + subId + "/disable"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // 发布事件，禁用的订阅者不应收到
        String eventJson = "{" +
            "\"eventType\":\"ENABLE_DISABLE_TEST\"," +
            "\"data\":\"{\\\"test\\\":\\\"data\\\"}\"" +
        "}";

        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson))
            .andExpect(status().isOk());

        // 验证禁用的订阅者不在匹配列表中
        List<Subscription> disabledSubscribers = subscriptionService.findSubscriptionsByEventType("ENABLE_DISABLE_TEST");
        assertEquals(0, disabledSubscribers.size());

        // 重新启用
        mockMvc.perform(post("/api/subscriptions/" + subId + "/enable"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // 验证启用后能够匹配
        List<Subscription> enabledSubscribers = subscriptionService.findSubscriptionsByEventType("ENABLE_DISABLE_TEST");
        assertEquals(1, enabledSubscribers.size());
    }

    @Test
    void testSubscriptionUpdate() throws Exception {
        // 创建订阅
        String subscriptionJson = "{" +
            "\"subscriberName\":\"更新测试\"," +
            "\"callbackUrl\":\"https://update-test.example.com/webhook\"," +
            "\"eventTypes\":[\"OLD_EVENT\"]," +
            "\"status\":1" +
        "}";

        MvcResult createResult = mockMvc.perform(post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriptionJson))
            .andExpect(status().isOk())
            .andReturn();

        String response = createResult.getResponse().getContentAsString();
        Long subId = objectMapper.readTree(response).get("data").get("id").asLong();

        // 更新订阅的事件类型
        String updateJson = "{" +
            "\"subscriberName\":\"更新后的名称\"," +
            "\"callbackUrl\":\"https://updated.example.com/webhook\"," +
            "\"eventTypes\":[\"NEW_EVENT_1\",\"NEW_EVENT_2\"]," +
            "\"status\":1" +
        "}";

        mockMvc.perform(put("/api/subscriptions/" + subId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.subscriberName").value("更新后的名称"))
            .andExpect(jsonPath("$.data.eventTypes", hasSize(2)));

        // 验证旧事件类型不再匹配
        List<Subscription> oldEventSubs = subscriptionService.findSubscriptionsByEventType("OLD_EVENT");
        assertEquals(0, oldEventSubs.size());

        // 验证新事件类型能够匹配
        List<Subscription> newEvent1Subs = subscriptionService.findSubscriptionsByEventType("NEW_EVENT_1");
        assertEquals(1, newEvent1Subs.size());

        List<Subscription> newEvent2Subs = subscriptionService.findSubscriptionsByEventType("NEW_EVENT_2");
        assertEquals(1, newEvent2Subs.size());
    }

    @Test
    void testPublishEventWithNoSubscribers() throws Exception {
        // 发布一个没有订阅者的事件
        String eventJson = "{" +
            "\"eventType\":\"NO_SUBSCRIBER_EVENT\"," +
            "\"data\":\"{\\\"message\\\":\\\"No one is listening\\\"}\"," +
            "\"bizId\":\"no-sub-test\"" +
        "}";

        // 应该成功发布，即使没有订阅者
        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.eventId").exists())
            .andExpect(jsonPath("$.message").value("事件发布成功，正在推送给订阅者"));
    }

    @Test
    void testSubscriptionDelete() throws Exception {
        // 创建订阅
        String subscriptionJson = "{" +
            "\"subscriberName\":\"删除测试\"," +
            "\"callbackUrl\":\"https://delete-test.example.com/webhook\"," +
            "\"eventTypes\":[\"DELETE_TEST_EVENT\"]," +
            "\"status\":1" +
        "}";

        MvcResult createResult = mockMvc.perform(post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(subscriptionJson))
            .andExpect(status().isOk())
            .andReturn();

        String response = createResult.getResponse().getContentAsString();
        Long subId = objectMapper.readTree(response).get("data").get("id").asLong();

        // 删除订阅
        mockMvc.perform(delete("/api/subscriptions/" + subId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // 验证订阅已删除
        mockMvc.perform(get("/api/subscriptions/" + subId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("订阅不存在"));

        // 验证删除后不能匹配事件
        List<Subscription> subscribers = subscriptionService.findSubscriptionsByEventType("DELETE_TEST_EVENT");
        assertEquals(0, subscribers.size());
    }
}
