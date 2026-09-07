package com.push.system.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.push.system.model.HttpResult;
import com.push.system.model.ThirdPartyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 响应解析器
 * 根据配置解析第三方系统的响应
 */
@Component
public class ResponseParser {

    private static final Logger logger = LoggerFactory.getLogger(ResponseParser.class);

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 解析响应
     */
    public HttpResult parse(String rawResponse, ThirdPartyConfig config) {
        try {
            // 1. 判断是否成功
            boolean success = evaluateSuccessCondition(rawResponse, config.getSuccessCondition());

            if (success) {
                // 2. 提取结果数据
                String resultData = extractResultData(rawResponse, config.getResultPath());
                return HttpResult.success(resultData, rawResponse, 0);
            } else {
                // 3. 提取错误信息
                String errorMessage = extractErrorMessage(rawResponse, config.getErrorMsgPath());
                return HttpResult.failure(0, errorMessage, rawResponse, 0);
            }

        } catch (Exception e) {
            logger.error("解析响应失败: " + e.getMessage(), e);
            return HttpResult.failure(0, "解析响应失败: " + e.getMessage(), rawResponse, 0);
        }
    }

    /**
     * 评估成功条件
     * 支持的表达式格式: $.code == 0, $.success == true, $.status == 'SUCCESS'
     */
    private boolean evaluateSuccessCondition(String response, String condition) {
        if (condition == null || condition.isEmpty()) {
            // 如果没有配置条件，默认认为成功
            return true;
        }

        try {
            // 解析条件表达式: $.code == 0
            String[] parts = condition.split("==");
            if (parts.length != 2) {
                logger.warn("成功条件格式错误: {}", condition);
                return false;
            }

            String jsonPath = parts[0].trim();
            String expectedValue = parts[1].trim().replace("'", "").replace("\"", "");

            // 使用JsonPath提取值
            Object actualValue = JsonPath.read(response, jsonPath);

            // 比较值
            return String.valueOf(actualValue).equals(expectedValue);

        } catch (Exception e) {
            logger.error("评估成功条件失败: " + condition, e);
            return false;
        }
    }

    /**
     * 提取结果数据
     */
    private String extractResultData(String response, String path) {
        if (path == null || path.isEmpty()) {
            return response;
        }

        try {
            Object result = JsonPath.read(response, path);
            if (result instanceof String) {
                return (String) result;
            } else {
                return objectMapper.writeValueAsString(result);
            }
        } catch (Exception e) {
            logger.warn("提取结果数据失败: " + path, e);
            return response;
        }
    }

    /**
     * 提取错误信息
     */
    private String extractErrorMessage(String response, String path) {
        if (path == null || path.isEmpty()) {
            return "第三方系统返回失败";
        }

        try {
            Object errorMsg = JsonPath.read(response, path);
            return String.valueOf(errorMsg);
        } catch (Exception e) {
            logger.warn("提取错误信息失败: " + path, e);
            return "第三方系统返回失败，无法解析错误信息";
        }
    }
}
