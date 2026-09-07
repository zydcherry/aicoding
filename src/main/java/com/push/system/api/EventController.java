package com.push.system.api;

import com.push.system.entity.Event;
import com.push.system.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 事件发布API控制器
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired
    private EventService eventService;

    /**
     * 发布事件
     *
     * 这是订阅-发布模式的核心API
     * 业务系统发布事件后，系统自动推送给所有订阅了该事件类型的订阅者
     */
    @PostMapping("/publish")
    public EventPublishResponse publishEvent(@RequestBody EventPublishRequest request) {
        try {
            // 参数校验
            if (request.getEventType() == null || request.getEventType().isEmpty()) {
                return EventPublishResponse.fail("事件类型不能为空");
            }
            if (request.getData() == null || request.getData().isEmpty()) {
                return EventPublishResponse.fail("事件数据不能为空");
            }

            // 发布事件
            String eventId = eventService.publishEvent(
                request.getEventType(),
                request.getData(),
                request.getBizId(),
                request.getSourceSystem()
            );

            return EventPublishResponse.success(eventId, "事件发布成功，正在推送给订阅者");

        } catch (Exception e) {
            return EventPublishResponse.fail("发布失败: " + e.getMessage());
        }
    }

    /**
     * 查询事件状态
     */
    @GetMapping("/{eventId}")
    public EventStatusResponse getEventStatus(@PathVariable String eventId) {
        Event event = eventService.getEvent(eventId);
        if (event == null) {
            return EventStatusResponse.fail("事件不存在");
        }

        return EventStatusResponse.success(event);
    }

    /**
     * 事件发布请求
     */
    public static class EventPublishRequest {
        private String eventType;     // 事件类型（必填）
        private String data;          // 事件数据JSON（必填）
        private String bizId;         // 业务ID（可选）
        private String sourceSystem;  // 来源系统（可选）

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getData() {
            return data;
        }

        public void setData(String data) {
            this.data = data;
        }

        public String getBizId() {
            return bizId;
        }

        public void setBizId(String bizId) {
            this.bizId = bizId;
        }

        public String getSourceSystem() {
            return sourceSystem;
        }

        public void setSourceSystem(String sourceSystem) {
            this.sourceSystem = sourceSystem;
        }
    }

    /**
     * 事件发布响应
     */
    public static class EventPublishResponse {
        private boolean success;
        private String eventId;
        private String message;

        public static EventPublishResponse success(String eventId, String message) {
            EventPublishResponse response = new EventPublishResponse();
            response.success = true;
            response.eventId = eventId;
            response.message = message;
            return response;
        }

        public static EventPublishResponse fail(String message) {
            EventPublishResponse response = new EventPublishResponse();
            response.success = false;
            response.message = message;
            return response;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getEventId() {
            return eventId;
        }

        public void setEventId(String eventId) {
            this.eventId = eventId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * 事件状态响应
     */
    public static class EventStatusResponse {
        private boolean success;
        private String message;
        private Event data;

        public static EventStatusResponse success(Event event) {
            EventStatusResponse response = new EventStatusResponse();
            response.success = true;
            response.message = "查询成功";
            response.data = event;
            return response;
        }

        public static EventStatusResponse fail(String message) {
            EventStatusResponse response = new EventStatusResponse();
            response.success = false;
            response.message = message;
            return response;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public Event getData() {
            return data;
        }

        public void setData(Event data) {
            this.data = data;
        }
    }
}
