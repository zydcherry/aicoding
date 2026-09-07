package com.push.system.model;

/**
 * HTTP调用结果
 */
public class HttpResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * HTTP状态码
     */
    private int statusCode;

    /**
     * 响应结果数据
     */
    private String resultData;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 原始响应体
     */
    private String rawResponse;

    /**
     * 耗时（ms）
     */
    private long costTime;

    public static HttpResult success(String resultData, String rawResponse, long costTime) {
        HttpResult result = new HttpResult();
        result.success = true;
        result.resultData = resultData;
        result.rawResponse = rawResponse;
        result.costTime = costTime;
        return result;
    }

    public static HttpResult failure(int statusCode, String errorMessage, String rawResponse, long costTime) {
        HttpResult result = new HttpResult();
        result.success = false;
        result.statusCode = statusCode;
        result.errorMessage = errorMessage;
        result.rawResponse = rawResponse;
        result.costTime = costTime;
        return result;
    }

    // Getters and Setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getResultData() {
        return resultData;
    }

    public void setResultData(String resultData) {
        this.resultData = resultData;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public long getCostTime() {
        return costTime;
    }

    public void setCostTime(long costTime) {
        this.costTime = costTime;
    }
}
