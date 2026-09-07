package com.push.system.http;

import com.push.system.config.PushSystemProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP执行器
 * 负责实际的HTTP调用
 */
@Component
public class HttpExecutor {

    private static final Logger logger = LoggerFactory.getLogger(HttpExecutor.class);

    @Autowired
    private PushSystemProperties pushSystemProperties;

    private RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        // 从配置中读取超时时间
        int connectTimeout = pushSystemProperties.getHttp().getDefaultConnectTimeout();
        int readTimeout = pushSystemProperties.getHttp().getDefaultReadTimeout();

        this.restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofMillis(connectTimeout))
            .setReadTimeout(Duration.ofMillis(readTimeout))
            .build();

        logger.info("HTTP执行器初始化完成，连接超时: {}ms, 读取超时: {}ms", connectTimeout, readTimeout);
    }

    /**
     * 执行HTTP请求
     */
    public String execute(String url, String method, Map<String, String> headers, String body, Integer timeout) throws IOException {
        try {
            // 构建请求头
            HttpHeaders httpHeaders = new HttpHeaders();
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    httpHeaders.add(entry.getKey(), entry.getValue());
                }
            }

            // 构建请求体
            HttpEntity<String> requestEntity = new HttpEntity<>(body, httpHeaders);

            // 执行请求
            ResponseEntity<String> response;
            if ("POST".equalsIgnoreCase(method)) {
                response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            } else if ("PUT".equalsIgnoreCase(method)) {
                response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, String.class);
            } else if ("GET".equalsIgnoreCase(method)) {
                response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            } else {
                throw new IllegalArgumentException("不支持的HTTP方法: " + method);
            }

            // 检查状态码
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                logger.warn("HTTP请求返回非2xx状态码: {}", response.getStatusCodeValue());
                throw new IOException("HTTP请求失败，状态码: " + response.getStatusCodeValue());
            }

        } catch (HttpStatusCodeException e) {
            logger.warn("HTTP请求失败，状态码: {}, 响应: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new IOException("HTTP请求失败，状态码: " + e.getStatusCode().value() + ", 响应: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error("HTTP请求异常", e);
            throw new IOException("HTTP请求异常: " + e.getMessage(), e);
        }
    }
}
