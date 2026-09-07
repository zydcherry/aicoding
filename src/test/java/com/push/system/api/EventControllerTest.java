package com.push.system.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.push.system.entity.Event;
import com.push.system.service.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 事件发布API测试
 */
@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventService eventService;

    @Test
    void testPublishEvent() throws Exception {
        // Mock事件ID
        String eventId = "evt_1788708433086_1";
        when(eventService.publishEvent(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(eventId);

        // 执行请求
        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"ORDER_CREATED\",\"data\":\"{\\\"orderId\\\":\\\"12345\\\"}\",\"bizId\":\"order-12345\",\"sourceSystem\":\"OrderService\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.eventId").value(eventId))
            .andExpect(jsonPath("$.message").value("事件发布成功，正在推送给订阅者"));

        verify(eventService, times(1)).publishEvent(
            eq("ORDER_CREATED"),
            eq("{\"orderId\":\"12345\"}"),
            eq("order-12345"),
            eq("OrderService")
        );
    }

    @Test
    void testPublishEventWithMissingEventType() throws Exception {
        // 测试缺少事件类型
        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"data\":\"{\\\"orderId\\\":\\\"12345\\\"}\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("事件类型不能为空"));

        verify(eventService, times(0)).publishEvent(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testPublishEventWithEmptyEventType() throws Exception {
        // 测试空事件类型
        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"\",\"data\":\"{\\\"orderId\\\":\\\"12345\\\"}\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("事件类型不能为空"));

        verify(eventService, times(0)).publishEvent(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testPublishEventWithMissingData() throws Exception {
        // 测试缺少事件数据
        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"ORDER_CREATED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("事件数据不能为空"));

        verify(eventService, times(0)).publishEvent(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testPublishEventWithEmptyData() throws Exception {
        // 测试空事件数据
        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"ORDER_CREATED\",\"data\":\"\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("事件数据不能为空"));

        verify(eventService, times(0)).publishEvent(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void testPublishEventWithOptionalFields() throws Exception {
        // 测试可选字段（bizId和sourceSystem）
        String eventId = "evt_test_123";
        when(eventService.publishEvent(anyString(), anyString(), isNull(), isNull()))
            .thenReturn(eventId);

        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"USER_REGISTERED\",\"data\":\"{\\\"userId\\\":\\\"999\\\"}\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.eventId").value(eventId));

        verify(eventService, times(1)).publishEvent(
            eq("USER_REGISTERED"),
            eq("{\"userId\":\"999\"}"),
            isNull(),
            isNull()
        );
    }

    @Test
    void testPublishEventWithException() throws Exception {
        // 测试发布异常
        when(eventService.publishEvent(anyString(), anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("发布失败"));

        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"TEST_EVENT\",\"data\":\"{}\",\"bizId\":\"test\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("发布失败: 发布失败"));
    }

    @Test
    void testGetEventStatus() throws Exception {
        // 准备测试数据
        Event event = new Event();
        event.setEventId("evt_test_123");
        event.setEventType("ORDER_CREATED");
        event.setEventData("{\"orderId\":\"12345\"}");
        event.setBizId("order-12345");
        event.setStatus(2); // 已完成

        when(eventService.getEvent("evt_test_123")).thenReturn(event);

        // 执行请求
        mockMvc.perform(get("/api/events/evt_test_123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.eventId").value("evt_test_123"))
            .andExpect(jsonPath("$.data.eventType").value("ORDER_CREATED"))
            .andExpect(jsonPath("$.data.status").value(2));

        verify(eventService, times(1)).getEvent("evt_test_123");
    }

    @Test
    void testGetEventStatusNotFound() throws Exception {
        // Mock事件不存在
        when(eventService.getEvent("non_existent_id")).thenReturn(null);

        // 执行请求
        mockMvc.perform(get("/api/events/non_existent_id"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("事件不存在"));

        verify(eventService, times(1)).getEvent("non_existent_id");
    }

    @Test
    void testPublishMultipleEvents() throws Exception {
        // 测试连续发布多个事件
        when(eventService.publishEvent(anyString(), anyString(), any(), any()))
            .thenReturn("evt_1")
            .thenReturn("evt_2")
            .thenReturn("evt_3");

        // 第一个事件
        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"EVENT_1\",\"data\":\"{}\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value("evt_1"));

        // 第二个事件
        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"EVENT_2\",\"data\":\"{}\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value("evt_2"));

        // 第三个事件
        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"EVENT_3\",\"data\":\"{}\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value("evt_3"));

        verify(eventService, times(3)).publishEvent(anyString(), anyString(), isNull(), isNull());
    }

    @Test
    void testPublishEventWithComplexData() throws Exception {
        // 测试复杂的事件数据
        String eventId = "evt_complex_123";
        String complexData = "{\"orderId\":\"12345\",\"items\":[{\"id\":1,\"name\":\"商品1\"},{\"id\":2,\"name\":\"商品2\"}],\"user\":{\"id\":\"USR001\",\"name\":\"张三\"}}";

        when(eventService.publishEvent(anyString(), eq(complexData), anyString(), any()))
            .thenReturn(eventId);

        mockMvc.perform(post("/api/events/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"ORDER_CREATED\",\"data\":\"" + complexData.replace("\"", "\\\"") + "\",\"bizId\":\"order-12345\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.eventId").value(eventId));
    }
}
