package com.push.system.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.push.system.entity.Subscription;
import com.push.system.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 订阅管理API测试
 */
@WebMvcTest(SubscriptionController.class)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SubscriptionService subscriptionService;

    @Test
    void testCreateSubscription() throws Exception {
        // 准备测试数据
        Subscription subscription = new Subscription();
        subscription.setId(1L);
        subscription.setSubscriberName("测试系统");
        subscription.setCallbackUrl("https://test.example.com/webhook");
        subscription.setEventTypes(Arrays.asList("TEST_EVENT"));
        subscription.setStatus(1);
        subscription.setCreatedAt(LocalDateTime.now());
        subscription.setUpdatedAt(LocalDateTime.now());

        when(subscriptionService.createSubscription(any(Subscription.class)))
            .thenReturn(subscription);

        // 执行请求
        mockMvc.perform(post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subscriberName\":\"测试系统\",\"callbackUrl\":\"https://test.example.com/webhook\",\"eventTypes\":[\"TEST_EVENT\"],\"status\":1}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("创建成功"))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.subscriberName").value("测试系统"))
            .andExpect(jsonPath("$.data.callbackUrl").value("https://test.example.com/webhook"));

        verify(subscriptionService, times(1)).createSubscription(any(Subscription.class));
    }

    @Test
    void testCreateSubscriptionWithMissingFields() throws Exception {
        // 测试缺少必填字段
        mockMvc.perform(post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subscriberName\":\"测试系统\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void testListAllSubscriptions() throws Exception {
        // 准备测试数据
        Subscription sub1 = new Subscription();
        sub1.setId(1L);
        sub1.setSubscriberName("系统1");
        sub1.setCallbackUrl("https://sys1.example.com/webhook");
        sub1.setEventTypes(Arrays.asList("EVENT_A"));
        sub1.setStatus(1);

        Subscription sub2 = new Subscription();
        sub2.setId(2L);
        sub2.setSubscriberName("系统2");
        sub2.setCallbackUrl("https://sys2.example.com/webhook");
        sub2.setEventTypes(Arrays.asList("EVENT_B"));
        sub2.setStatus(1);

        List<Subscription> subscriptions = Arrays.asList(sub1, sub2);
        when(subscriptionService.listSubscriptions()).thenReturn(subscriptions);

        // 执行请求
        mockMvc.perform(get("/api/subscriptions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].subscriberName").value("系统1"))
            .andExpect(jsonPath("$.data[1].subscriberName").value("系统2"));

        verify(subscriptionService, times(1)).listSubscriptions();
    }

    @Test
    void testGetSubscription() throws Exception {
        // 准备测试数据
        Subscription subscription = new Subscription();
        subscription.setId(1L);
        subscription.setSubscriberName("查询测试");
        subscription.setCallbackUrl("https://query.example.com/webhook");
        subscription.setEventTypes(Arrays.asList("QUERY_EVENT"));
        subscription.setStatus(1);

        when(subscriptionService.getSubscription(1L)).thenReturn(subscription);

        // 执行请求
        mockMvc.perform(get("/api/subscriptions/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1))
            .andExpect(jsonPath("$.data.subscriberName").value("查询测试"));

        verify(subscriptionService, times(1)).getSubscription(1L);
    }

    @Test
    void testGetSubscriptionNotFound() throws Exception {
        // Mock不存在的订阅
        when(subscriptionService.getSubscription(999L)).thenReturn(null);

        // 执行请求
        mockMvc.perform(get("/api/subscriptions/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("订阅不存在"));

        verify(subscriptionService, times(1)).getSubscription(999L);
    }

    @Test
    void testUpdateSubscription() throws Exception {
        // 准备测试数据
        Subscription updated = new Subscription();
        updated.setId(1L);
        updated.setSubscriberName("更新后的名称");
        updated.setCallbackUrl("https://updated.example.com/webhook");
        updated.setEventTypes(Arrays.asList("NEW_EVENT"));
        updated.setStatus(1);

        when(subscriptionService.updateSubscription(eq(1L), any(Subscription.class)))
            .thenReturn(updated);

        // 执行请求
        mockMvc.perform(put("/api/subscriptions/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subscriberName\":\"更新后的名称\",\"callbackUrl\":\"https://updated.example.com/webhook\",\"eventTypes\":[\"NEW_EVENT\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.subscriberName").value("更新后的名称"));

        verify(subscriptionService, times(1)).updateSubscription(eq(1L), any(Subscription.class));
    }

    @Test
    void testDeleteSubscription() throws Exception {
        // Mock删除成功（deleteSubscription返回void）
        doNothing().when(subscriptionService).deleteSubscription(1L);

        // 执行请求
        mockMvc.perform(delete("/api/subscriptions/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("删除成功"));

        verify(subscriptionService, times(1)).deleteSubscription(1L);
    }

    @Test
    void testDeleteSubscriptionNotFound() throws Exception {
        // Mock删除失败（抛出异常）
        doThrow(new IllegalArgumentException("订阅不存在")).when(subscriptionService).deleteSubscription(999L);

        // 执行请求
        mockMvc.perform(delete("/api/subscriptions/999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false));

        verify(subscriptionService, times(1)).deleteSubscription(999L);
    }

    @Test
    void testEnableSubscription() throws Exception {
        // Mock更新成功
        Subscription enabled = new Subscription();
        enabled.setId(1L);
        enabled.setStatus(1);
        when(subscriptionService.updateSubscription(eq(1L), any(Subscription.class)))
            .thenReturn(enabled);

        // 执行请求
        mockMvc.perform(post("/api/subscriptions/1/enable"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("启用成功"));

        verify(subscriptionService, times(1)).updateSubscription(eq(1L), any(Subscription.class));
    }

    @Test
    void testDisableSubscription() throws Exception {
        // Mock更新成功
        Subscription disabled = new Subscription();
        disabled.setId(1L);
        disabled.setStatus(0);
        when(subscriptionService.updateSubscription(eq(1L), any(Subscription.class)))
            .thenReturn(disabled);

        // 执行请求
        mockMvc.perform(post("/api/subscriptions/1/disable"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("禁用成功"));

        verify(subscriptionService, times(1)).updateSubscription(eq(1L), any(Subscription.class));
    }

    @Test
    void testCreateSubscriptionWithMultipleEventTypes() throws Exception {
        // 测试多个事件类型
        Subscription subscription = new Subscription();
        subscription.setId(1L);
        subscription.setSubscriberName("多事件系统");
        subscription.setCallbackUrl("https://multi.example.com/webhook");
        subscription.setEventTypes(Arrays.asList("EVENT_1", "EVENT_2", "EVENT_3"));
        subscription.setStatus(1);

        when(subscriptionService.createSubscription(any(Subscription.class)))
            .thenReturn(subscription);

        mockMvc.perform(post("/api/subscriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"subscriberName\":\"多事件系统\",\"callbackUrl\":\"https://multi.example.com/webhook\",\"eventTypes\":[\"EVENT_1\",\"EVENT_2\",\"EVENT_3\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.eventTypes", hasSize(3)));
    }
}
