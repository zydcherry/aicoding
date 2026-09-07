package com.push.system.http;

import com.push.system.model.HttpResult;
import com.push.system.model.PushMessage;
import com.push.system.model.ThirdPartyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一HTTP客户端
 * 根据配置调用不同第三方系统的HTTP API
 */
@Component
public class UnifiedHttpClient {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedHttpClient.class);

    @Autowired
    private TemplateEngine templateEngine;

    @Autowired
    private ResponseParser responseParser;

    @Autowired
    private AuthHandler authHandler;

    @Autowired
    private HttpExecutor httpExecutor;

    /**
     * 调用第三方HTTP API
     */
    public HttpResult call(PushMessage message, ThirdPartyConfig config) {
        long startTime = System.currentTimeMillis();

        try {
            logger.info("开始调用第三方API, pushId: {}, thirdParty: {}, url: {}",
                message.getId(), config.getCode(), config.getApiUrl());

            // 1. 构建请求Header
            Map<String, String> headers = buildHeaders(message, config);

            // 2. 构建请求Body
            String body = buildBody(message, config);

            logger.debug("请求Header: {}", headers);
            logger.debug("请求Body: {}", body);

            // 3. 执行HTTP调用
            String rawResponse = httpExecutor.execute(
                config.getApiUrl(),
                config.getMethod(),
                headers,
                body,
                config.getReadTimeout()
            );

            long costTime = System.currentTimeMillis() - startTime;

            logger.debug("响应Body: {}", rawResponse);

            // 4. 解析响应结果
            HttpResult result = responseParser.parse(rawResponse, config);
            result.setCostTime(costTime);

            if (result.isSuccess()) {
                logger.info("第三方API调用成功, pushId: {}, thirdParty: {}, costTime: {}ms",
                    message.getId(), config.getCode(), costTime);
            } else {
                logger.warn("第三方API调用失败, pushId: {}, thirdParty: {}, error: {}, costTime: {}ms",
                    message.getId(), config.getCode(), result.getErrorMessage(), costTime);
            }

            return result;

        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            logger.error("第三方API调用异常, pushId: " + message.getId() + ", thirdParty: " + config.getCode(), e);
            return HttpResult.failure(0, e.getMessage(), null, costTime);
        }
    }

    /**
     * 构建请求Header
     */
    private Map<String, String> buildHeaders(PushMessage message, ThirdPartyConfig config) {
        Map<String, String> headers = new HashMap<>();

        if (config.getHeaderTemplate() != null) {
            for (Map.Entry<String, String> entry : config.getHeaderTemplate().entrySet()) {
                // 替换模板变量
                String value = templateEngine.render(entry.getValue(), message, config);
                headers.put(entry.getKey(), value);
            }
        }

        // 添加认证信息
        authHandler.addAuthHeaders(headers, message, config);

        return headers;
    }

    /**
     * 构建请求Body
     */
    private String buildBody(PushMessage message, ThirdPartyConfig config) {
        if (config.getBodyTemplate() == null || config.getBodyTemplate().isEmpty()) {
            return "";
        }

        // 替换模板变量
        return templateEngine.render(config.getBodyTemplate(), message, config);
    }
}
