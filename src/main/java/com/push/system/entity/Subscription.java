package com.push.system.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订阅实体
 */
public class Subscription {

    private Long id;
    private String subscriberName;
    private String callbackUrl;
    private List<String> eventTypes;  // 订阅的事件类型列表
    private String eventTypesJson;    // 数据库存储的JSON字符串
    private String secretKey;
    private Integer status;  // 0-禁用 1-启用
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSubscriberName() {
        return subscriberName;
    }

    public void setSubscriberName(String subscriberName) {
        this.subscriberName = subscriberName;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public List<String> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(List<String> eventTypes) {
        this.eventTypes = eventTypes;
        // 同时更新JSON字符串
        if (eventTypes != null) {
            try {
                this.eventTypesJson = objectMapper.writeValueAsString(eventTypes);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize eventTypes", e);
            }
        }
    }

    public String getEventTypesJson() {
        return eventTypesJson;
    }

    public void setEventTypesJson(String eventTypesJson) {
        this.eventTypesJson = eventTypesJson;
        // 同时解析为List
        if (eventTypesJson != null && !eventTypesJson.isEmpty()) {
            try {
                this.eventTypes = objectMapper.readValue(eventTypesJson, new TypeReference<List<String>>() {});
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize eventTypes", e);
            }
        }
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
