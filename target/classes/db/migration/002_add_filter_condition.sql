-- 添加订阅过滤条件字段
-- 执行日期: 2026-09-07

USE push_system;

-- 为subscription表添加filter_condition字段
ALTER TABLE subscription ADD COLUMN filter_condition JSON COMMENT '过滤条件(JSON对象,用于细粒度订阅)' AFTER description;
