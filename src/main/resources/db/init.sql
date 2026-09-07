-- 推送系统数据库初始化脚本（更新版）

-- 创建数据库
CREATE DATABASE IF NOT EXISTS push_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE push_system;

-- =============================================
-- 1. 订阅管理表
-- =============================================

-- 订阅表
CREATE TABLE subscription (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    subscriber_name VARCHAR(100) NOT NULL COMMENT '订阅者名称',
    callback_url VARCHAR(500) NOT NULL COMMENT '回调URL',
    event_types TEXT NOT NULL COMMENT '订阅的事件类型列表(JSON数组)',
    secret_key VARCHAR(128) COMMENT '签名密钥',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-禁用 1-启用',
    description VARCHAR(500) COMMENT '描述说明',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_subscriber_name (subscriber_name)
) COMMENT='订阅表';

-- =============================================
-- 2. 事件管理表
-- =============================================

-- 事件表
CREATE TABLE event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    event_id VARCHAR(64) UNIQUE NOT NULL COMMENT '事件ID（业务唯一标识）',
    event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
    event_data TEXT NOT NULL COMMENT '事件数据(JSON)',
    biz_id VARCHAR(100) COMMENT '业务ID',
    source_system VARCHAR(64) COMMENT '来源系统',
    status TINYINT DEFAULT 0 COMMENT '处理状态: 0-待处理 1-处理中 2-已完成',
    subscriber_count INT DEFAULT 0 COMMENT '订阅者数量',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_event_id (event_id),
    INDEX idx_event_type (event_type),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_biz_id (biz_id)
) COMMENT='事件表';

-- =============================================
-- 3. 第三方系统配置表（保留，用于点对点推送）
-- =============================================

-- 第三方系统配置表
CREATE TABLE third_party_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) UNIQUE NOT NULL COMMENT '第三方编码',
    name VARCHAR(100) NOT NULL COMMENT '第三方名称',
    api_url VARCHAR(500) NOT NULL COMMENT 'API地址',
    method VARCHAR(10) DEFAULT 'POST' COMMENT 'HTTP方法',
    connect_timeout INT DEFAULT 3000 COMMENT '连接超时(ms)',
    read_timeout INT DEFAULT 5000 COMMENT '读取超时(ms)',
    header_template JSON COMMENT 'Header模板',
    body_template TEXT COMMENT 'Body模板',
    success_condition VARCHAR(200) COMMENT '成功条件表达式',
    result_path VARCHAR(100) COMMENT '结果路径',
    error_msg_path VARCHAR(100) COMMENT '错误信息路径',
    auth_type VARCHAR(20) COMMENT '认证类型: none/apikey/sign/oauth2',
    auth_params JSON COMMENT '认证参数',
    max_retry INT DEFAULT 3 COMMENT '最大重试次数',
    retry_strategy VARCHAR(20) DEFAULT 'exponential' COMMENT '重试策略: fixed/exponential',
    retry_interval INT DEFAULT 1000 COMMENT '重试间隔(ms)',
    qps_limit INT COMMENT 'QPS限制',
    daily_limit INT COMMENT '日调用量限制',
    fail_alert_threshold INT DEFAULT 10 COMMENT '失败告警阈值',
    alert_receivers VARCHAR(500) COMMENT '告警接收人',
    status TINYINT DEFAULT 1 COMMENT '状态: 0-停用 1-启用',
    ext_config JSON COMMENT '扩展配置',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_code (code),
    INDEX idx_status (status)
) COMMENT='第三方系统配置表';

-- =============================================
-- 4. 通知记录表（支持两种模式）
-- =============================================

-- 通知记录表
CREATE TABLE notification_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '通知记录ID',
    notification_id VARCHAR(64) UNIQUE NOT NULL COMMENT '通知唯一ID',

    -- 关联信息
    event_id VARCHAR(64) COMMENT '事件ID（订阅模式）',
    subscription_id BIGINT COMMENT '订阅ID（订阅模式）',
    third_party_code VARCHAR(50) COMMENT '第三方编码（点对点模式）',

    -- 推送信息
    push_mode VARCHAR(20) NOT NULL COMMENT '推送模式: EVENT(事件驱动) / DIRECT(点对点)',
    callback_url VARCHAR(500) NOT NULL COMMENT '回调URL',
    request_data TEXT COMMENT '请求数据',
    response_data TEXT COMMENT '响应数据',

    -- 状态信息
    status TINYINT NOT NULL COMMENT '状态: 0-待推送 1-推送中 2-成功 3-失败',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    error_message TEXT COMMENT '错误信息',
    cost_time INT COMMENT '耗时(ms)',

    -- 时间信息
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    pushed_at TIMESTAMP COMMENT '推送完成时间',
    next_retry_at TIMESTAMP COMMENT '下次重试时间',

    INDEX idx_notification_id (notification_id),
    INDEX idx_event_id (event_id),
    INDEX idx_subscription_id (subscription_id),
    INDEX idx_third_party_code (third_party_code),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    INDEX idx_next_retry_at (next_retry_at)
) COMMENT='通知记录表（支持事件驱动和点对点两种模式）';

-- =============================================
-- 5. 推送失败记录表
-- =============================================

-- 推送失败记录表
CREATE TABLE push_failure (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    notification_id VARCHAR(64) NOT NULL COMMENT '通知ID',
    event_id VARCHAR(64) COMMENT '事件ID',
    subscription_id BIGINT COMMENT '订阅ID',
    third_party_code VARCHAR(50) COMMENT '第三方编码',
    failure_reason VARCHAR(500) COMMENT '失败原因',
    error_code VARCHAR(50) COMMENT '错误码',
    error_message TEXT COMMENT '错误详情',
    retry_count INT COMMENT '失败时的重试次数',
    request_data TEXT COMMENT '请求数据',
    response_data TEXT COMMENT '响应数据',
    is_alerted TINYINT DEFAULT 0 COMMENT '是否已告警',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notification_id (notification_id),
    INDEX idx_event_id (event_id),
    INDEX idx_subscription_id (subscription_id),
    INDEX idx_third_party_code (third_party_code),
    INDEX idx_created_at (created_at)
) COMMENT='推送失败记录表';

-- =============================================
-- 6. 插入示例数据
-- =============================================

-- 插入订阅示例
INSERT INTO subscription (subscriber_name, callback_url, event_types, description, status)
VALUES
('CRM系统', 'https://crm.example.com/webhook', '["USER_REGISTERED","ORDER_CREATED"]', 'CRM系统订阅用户和订单事件', 1),
('物流系统', 'https://logistics.example.com/webhook', '["ORDER_CREATED","ORDER_SHIPPED"]', '物流系统订阅订单事件', 1),
('财务系统', 'https://finance.example.com/webhook', '["ORDER_PAID"]', '财务系统订阅支付事件', 1);

-- 插入第三方配置示例（点对点推送）
INSERT INTO third_party_config (code, name, api_url, method, connect_timeout, read_timeout,
    header_template, body_template, success_condition, result_path, error_msg_path,
    auth_type, auth_params, max_retry, retry_strategy, status)
VALUES (
    'supplier-a',
    '供应商A系统',
    'https://api.supplier-a.com/order/push',
    'POST',
    3000,
    5000,
    '{"Content-Type":"application/json","X-API-Key":"${config.apiKey}","X-Request-Id":"${message.id}"}',
    '{"orderId":"${message.bizId}","data":${message.content},"timestamp":${timestamp}}',
    '$.code == 0',
    '$.data',
    '$.msg',
    'apikey',
    '{"apiKey":"ak_supplier_a_test123"}',
    3,
    'exponential',
    1
);
