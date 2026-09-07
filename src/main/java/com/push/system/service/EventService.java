package com.push.system.service;

import com.push.system.entity.Event;
import com.push.system.entity.Subscription;
import com.push.system.mapper.EventMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件服务
 */
@Service
public class EventService {

    private static final Logger logger = LoggerFactory.getLogger(EventService.class);

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private EventPublisher eventPublisher;

    @Autowired
    private EventMapper eventMapper;

    private final AtomicLong idGenerator = new AtomicLong(1);

    /**
     * 发布事件
     *
     * @param eventType 事件类型
     * @param eventData 事件数据
     * @param bizId 业务ID（可选）
     * @param sourceSystem 来源系统（可选）
     * @return 事件ID
     */
    public String publishEvent(String eventType, String eventData, String bizId, String sourceSystem) {
        // 1. 创建事件记录
        Event event = new Event();
        String eventId = generateEventId();
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setEventData(eventData);
        event.setBizId(bizId);
        event.setSourceSystem(sourceSystem);
        event.setStatus(0);  // 待处理
        event.setCreatedAt(LocalDateTime.now());

        logger.info("事件发布, eventId: {}, eventType: {}, bizId: {}", eventId, eventType, bizId);

        // 2. 查找订阅者
        List<Subscription> subscriptions = subscriptionService.findSubscriptionsByEventType(eventType);

        if (subscriptions.isEmpty()) {
            logger.warn("事件没有订阅者, eventId: {}, eventType: {}", eventId, eventType);
            event.setStatus(2);  // 已完成（无订阅者）
            event.setSubscriberCount(0);
        } else {
            logger.info("事件找到{}个订阅者, eventId: {}, eventType: {}", subscriptions.size(), eventId, eventType);
            event.setStatus(1);  // 处理中
            event.setSubscriberCount(subscriptions.size());
        }

        // 3. 保存事件到数据库
        eventMapper.insert(event);

        // 4. 异步推送给所有订阅者
        if (!subscriptions.isEmpty()) {
            eventPublisher.publishToSubscribers(event, subscriptions);
        }

        return eventId;
    }

    /**
     * 查询事件
     */
    public Event getEvent(String eventId) {
        return eventMapper.selectByEventId(eventId);
    }

    /**
     * 更新事件状态
     */
    public void updateEventStatus(String eventId, Integer status) {
        eventMapper.updateStatus(eventId, status);
        logger.debug("更新事件状态, eventId: {}, status: {}", eventId, status);
    }

    /**
     * 生成事件ID
     */
    private String generateEventId() {
        return "evt_" + System.currentTimeMillis() + "_" + idGenerator.getAndIncrement();
    }
}
