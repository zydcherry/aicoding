package com.push.system.api;

import com.push.system.model.PushMessage;
import java.time.LocalDateTime;

/**
 * 推送请求对象
 */
public class PushRequest {

    /**
     * 业务方提供的业务ID（可选）
     */
    private String bizId;

    /**
     * 消息优先级
     */
    private PushMessage.Priority priority;

    /**
     * 第三方系统编码
     */
    private String thirdPartyCode;

    /**
     * 消息类型
     */
    private String messageType;

    /**
     * 消息内容（JSON字符串）
     */
    private String content;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 扩展信息
     */
    private String extInfo;

    // Getters and Setters

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public PushMessage.Priority getPriority() {
        return priority != null ? priority : PushMessage.Priority.NORMAL;
    }

    public void setPriority(PushMessage.Priority priority) {
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

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public String getExtInfo() {
        return extInfo;
    }

    public void setExtInfo(String extInfo) {
        this.extInfo = extInfo;
    }
}
