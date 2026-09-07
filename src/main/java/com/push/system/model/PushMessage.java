package com.push.system.model;

import java.time.LocalDateTime;

/**
 * 推送消息统一模型
 */
public class PushMessage {

    /**
     * 系统生成的唯一ID
     */
    private Long id;

    /**
     * 业务方提供的业务ID（可选）
     */
    private String bizId;

    /**
     * 消息优先级
     */
    private Priority priority;

    /**
     * 第三方系统编码
     */
    private String thirdPartyCode;

    /**
     * 消息类型（如：ORDER、INVENTORY、LOGISTICS等）
     */
    private String messageType;

    /**
     * 消息内容（JSON字符串）
     */
    private String content;

    /**
     * 接入方式
     */
    private String accessType;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;

    /**
     * 扩展字段
     */
    private String extInfo;

    /**
     * 优先级枚举
     */
    public enum Priority {
        HIGH(1, "高优先级"),
        NORMAL(2, "普通优先级"),
        LOW(3, "低优先级");

        private final int level;
        private final String desc;

        Priority(int level, String desc) {
            this.level = level;
            this.desc = desc;
        }

        public int getLevel() {
            return level;
        }

        public String getDesc() {
            return desc;
        }
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public String getThirdPartyCode() {
        return thirdPartyCode;
    }

    public void setThirdPartyCode(String thirdPartyCode) {
        this.thirdPartyCode = thirdPartyCode;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAccessType() {
        return accessType;
    }

    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetryCount() {
        return maxRetryCount;
    }

    public void setMaxRetryCount(Integer maxRetryCount) {
        this.maxRetryCount = maxRetryCount;
    }

    public String getExtInfo() {
        return extInfo;
    }

    public void setExtInfo(String extInfo) {
        this.extInfo = extInfo;
    }
}
