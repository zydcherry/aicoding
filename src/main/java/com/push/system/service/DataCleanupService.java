package com.push.system.service;

import com.push.system.mapper.EventMapper;
import com.push.system.mapper.NotificationRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 数据清理服务
 * 定期清理历史数据，避免数据库无限增长
 */
@Service
public class DataCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(DataCleanupService.class);

    @Autowired
    private EventMapper eventMapper;

    @Autowired
    private NotificationRecordMapper notificationRecordMapper;

    @Value("${push.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${push.cleanup.event-retention-days:30}")
    private int eventRetentionDays;

    @Value("${push.cleanup.notification-retention-days:90}")
    private int notificationRetentionDays;

    /**
     * 定时清理历史事件数据（每天凌晨3点执行）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanOldEvents() {
        if (!cleanupEnabled) {
            logger.debug("数据清理功能已禁用");
            return;
        }

        try {
            LocalDateTime beforeDate = LocalDateTime.now().minusDays(eventRetentionDays);
            int deletedCount = eventMapper.deleteOldEvents(beforeDate);

            if (deletedCount > 0) {
                logger.info("清理历史事件数据完成，删除{}条记录（{}天前）", deletedCount, eventRetentionDays);
            } else {
                logger.debug("无需清理的历史事件数据");
            }

        } catch (Exception e) {
            logger.error("清理历史事件数据失败", e);
        }
    }

    /**
     * 定时清理历史通知记录（每天凌晨4点执行）
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanOldNotifications() {
        if (!cleanupEnabled) {
            logger.debug("数据清理功能已禁用");
            return;
        }

        try {
            LocalDateTime beforeDate = LocalDateTime.now().minusDays(notificationRetentionDays);
            int deletedCount = notificationRecordMapper.deleteOldRecords(beforeDate);

            if (deletedCount > 0) {
                logger.info("清理历史通知记录完成，删除{}条记录（{}天前）", deletedCount, notificationRetentionDays);
            } else {
                logger.debug("无需清理的历史通知记录");
            }

        } catch (Exception e) {
            logger.error("清理历史通知记录失败", e);
        }
    }

    /**
     * 手动触发清理（供管理接口调用）
     */
    public void manualCleanup() {
        logger.info("手动触发数据清理");
        cleanOldEvents();
        cleanOldNotifications();
    }
}
