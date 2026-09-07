package com.push.system.model;

import java.util.Map;

/**
 * 第三方系统配置模型
 */
public class ThirdPartyConfig {

    // 基本信息
    private Long id;
    private String code;              // 第三方系统编码（唯一标识）
    private String name;              // 第三方系统名称

    // HTTP配置
    private String apiUrl;            // API完整地址
    private String method;            // HTTP方法: POST/PUT/GET
    private Integer connectTimeout;   // 连接超时（ms）
    private Integer readTimeout;      // 读取超时（ms）

    // Header配置（模板化）
    private Map<String, String> headerTemplate;

    // Body配置（模板化）
    private String bodyTemplate;

    // 响应解析配置
    private String successCondition;  // 成功条件表达式，如: $.code == 0
    private String resultPath;        // 结果路径，如: $.data
    private String errorMsgPath;      // 错误信息路径，如: $.message

    // 认证配置
    private String authType;          // 认证类型: none/apikey/sign/oauth2
    private Map<String, String> authParams;

    // 重试配置
    private Integer maxRetry;         // 最大重试次数
    private String retryStrategy;     // 重试策略: fixed/exponential
    private Integer retryInterval;    // 重试间隔（ms）

    // 限流配置
    private Integer qpsLimit;         // QPS限制
    private Integer dailyLimit;       // 日调用量限制

    // 告警配置
    private Integer failAlertThreshold; // 失败告警阈值
    private String alertReceivers;    // 告警接收人

    // 状态
    private Integer status;           // 状态: 0-停用 1-启用

    // 扩展字段
    private Map<String, Object> extConfig;

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Integer getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Integer connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Integer getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Integer readTimeout) {
        this.readTimeout = readTimeout;
    }

    public Map<String, String> getHeaderTemplate() {
        return headerTemplate;
    }

    public void setHeaderTemplate(Map<String, String> headerTemplate) {
        this.headerTemplate = headerTemplate;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public void setBodyTemplate(String bodyTemplate) {
        this.bodyTemplate = bodyTemplate;
    }

    public String getSuccessCondition() {
        return successCondition;
    }

    public void setSuccessCondition(String successCondition) {
        this.successCondition = successCondition;
    }

    public String getResultPath() {
        return resultPath;
    }

    public void setResultPath(String resultPath) {
        this.resultPath = resultPath;
    }

    public String getErrorMsgPath() {
        return errorMsgPath;
    }

    public void setErrorMsgPath(String errorMsgPath) {
        this.errorMsgPath = errorMsgPath;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public Map<String, String> getAuthParams() {
        return authParams;
    }

    public void setAuthParams(Map<String, String> authParams) {
        this.authParams = authParams;
    }

    public Integer getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(Integer maxRetry) {
        this.maxRetry = maxRetry;
    }

    public String getRetryStrategy() {
        return retryStrategy;
    }

    public void setRetryStrategy(String retryStrategy) {
        this.retryStrategy = retryStrategy;
    }

    public Integer getRetryInterval() {
        return retryInterval;
    }

    public void setRetryInterval(Integer retryInterval) {
        this.retryInterval = retryInterval;
    }

    public Integer getQpsLimit() {
        return qpsLimit;
    }

    public void setQpsLimit(Integer qpsLimit) {
        this.qpsLimit = qpsLimit;
    }

    public Integer getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(Integer dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public Integer getFailAlertThreshold() {
        return failAlertThreshold;
    }

    public void setFailAlertThreshold(Integer failAlertThreshold) {
        this.failAlertThreshold = failAlertThreshold;
    }

    public String getAlertReceivers() {
        return alertReceivers;
    }

    public void setAlertReceivers(String alertReceivers) {
        this.alertReceivers = alertReceivers;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Map<String, Object> getExtConfig() {
        return extConfig;
    }

    public void setExtConfig(Map<String, Object> extConfig) {
        this.extConfig = extConfig;
    }
}
