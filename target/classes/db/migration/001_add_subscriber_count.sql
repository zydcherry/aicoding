-- 为event表添加subscriber_count字段
-- 执行日期: 2026-09-07

USE push_system;

-- 添加subscriber_count字段
ALTER TABLE event ADD COLUMN subscriber_count INT DEFAULT 0 COMMENT '订阅者数量' AFTER status;
