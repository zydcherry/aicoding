package com.push.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.push.system.core.UnifiedPusher;
import com.push.system.entity.Event;
import com.push.system.entity.Subscription;
import com.push.system.model.PushMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 事件发布器
 * 负责将事件推送给订阅者
 */
@Service
public class EventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(EventPublisher.class);

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UnifiedPusher unifiedPusher;

    /**
     * 异步推送给所有订阅者
     */
    @Async
    public void publishToSubscribers(Event event, List<Subscription> subscriptions) {
        logger.info("开始推送事件给订阅者, eventId: {}, 订阅者数量: {}",
            event.getEventId(), subscriptions.size());

        for (Subscription subscription : subscriptions) {
            try {
                // 构建推送消息
                PushMessage message = buildPushMessage(event, subscription);

                // 提交到UnifiedPusher（事件驱动模式会自动识别）
                Long messageId = unifiedPusher.submitForDirectPush(message);

                logger.info("事件推送消息已提交, eventId: {}, messageId: {}, subscriptionId: {}, subscriber: {}",
                    event.getEventId(), messageId, subscription.getId(), subscription.getSubscriberName());

            } catch (Exception e) {
                logger.error("事件推送失败, eventId: " + event.getEventId() +
                    ", subscriptionId: " + subscription.getId(), e);
            }
        }
    }

    /**
     * 构建推送消息
     */
    private PushMessage buildPushMessage(Event event, Subscription subscription) {
        PushMessage message = new PushMessage();

        // 生成通知ID
        String notificationId = generateNotificationId(event.getEventId(), subscription.getId());
        message.setId(Long.parseLong(String.valueOf(notificationId.hashCode() & 0x7FFFFFFF)));

        // 设置基本信息
        message.setBizId(event.getBizId());
        message.setPriority(PushMessage.Priority.NORMAL);  // 事件推送默认普通优先级
        message.setMessageType(event.getEventType());
        message.setAccessType("EVENT");  // 标识为事件驱动模式

        // 设置推送目标（订阅者的回调URL）
        message.setThirdPartyCode("subscription_" + subscription.getId());

        // 设置消息内容（包装事件数据）
        try {
            Map<String, Object> content = new HashMap<>();
            content.put("eventId", event.getEventId());
            content.put("eventType", event.getEventType());
            content.put("eventData", objectMapper.readTree(event.getEventData()));
            content.put("bizId", event.getBizId());
            content.put("timestamp", System.currentTimeMillis());

            message.setContent(objectMapper.writeValueAsString(content));
        } catch (Exception e) {
            logger.error("构建消息内容失败", e);
            message.setContent(event.getEventData());
        }

        // 设置时间
        message.setCreateTime(LocalDateTime.now());
        message.setRetryCount(0);
        message.setMaxRetryCount(3);

        // 扩展信息
        try {
            Map<String, Object> extInfo = new HashMap<>();
            extInfo.put("eventId", event.getEventId());
            extInfo.put("subscriptionId", subscription.getId());
            extInfo.put("subscriberName", subscription.getSubscriberName());
            extInfo.put("callbackUrl", subscription.getCallbackUrl());
            extInfo.put("secretKey", subscription.getSecretKey());
            message.setExtInfo(objectMapper.writeValueAsString(extInfo));
        } catch (Exception e) {
            logger.error("构建扩展信息失败", e);
        }

        return message;
    }

    /**
     * 生成通知ID
     */
    private String generateNotificationId(String eventId, Long subscriptionId) {
        return "ntf_" + eventId + "_" + subscriptionId;
    }
}
