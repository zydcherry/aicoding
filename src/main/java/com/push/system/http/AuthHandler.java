package com.push.system.http;

import com.push.system.model.PushMessage;
import com.push.system.model.ThirdPartyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证处理器
 * 处理不同的认证方式：API Key、签名、OAuth2.0等
 */
@Component
public class AuthHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuthHandler.class);

    // OAuth2 Token缓存: thirdPartyCode -> TokenInfo
    private final Map<String, TokenInfo> tokenCache = new ConcurrentHashMap<>();

    /**
     * 添加认证Header
     */
    public void addAuthHeaders(Map<String, String> headers, PushMessage message, ThirdPartyConfig config) {
        String authType = config.getAuthType();
        if (authType == null || "none".equals(authType)) {
            return;
        }

        switch (authType) {
            case "apikey":
                handleApiKeyAuth(headers, config);
                break;
            case "sign":
                handleSignAuth(headers, message, config);
                break;
            case "oauth2":
                handleOAuth2Auth(headers, config);
                break;
            default:
                logger.warn("不支持的认证类型: {}", authType);
        }
    }

    /**
     * API Key认证
     */
    private void handleApiKeyAuth(Map<String, String> headers, ThirdPartyConfig config) {
        Map<String, String> authParams = config.getAuthParams();
        if (authParams != null && authParams.containsKey("apiKey")) {
            // API Key通常已经在headerTemplate中配置，这里不需要额外处理
            logger.debug("使用API Key认证");
        }
    }

    /**
     * 签名认证
     */
    private void handleSignAuth(Map<String, String> headers, PushMessage message, ThirdPartyConfig config) {
        try {
            Map<String, String> authParams = config.getAuthParams();
            if (authParams == null) {
                logger.warn("签名认证缺少authParams配置");
                return;
            }

            String appKey = authParams.get("appKey");
            String secretKey = authParams.get("secretKey");
            String signAlgorithm = authParams.getOrDefault("signAlgorithm", "MD5");

            // 获取timestamp（如果header中有的话）
            String timestamp = headers.get("Timestamp");
            if (timestamp == null) {
                timestamp = String.valueOf(System.currentTimeMillis());
                headers.put("Timestamp", timestamp);
            }

            // 构建签名字符串
            // 规则: appKey + timestamp + body + secretKey
            String body = headers.getOrDefault("X-Request-Body", "");
            String signStr = appKey + timestamp + body + secretKey;

            // 计算签名
            String sign = calculateSign(signStr, signAlgorithm);

            // 替换Header中的${sign}占位符
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getValue().contains("${sign}")) {
                    headers.put(entry.getKey(), entry.getValue().replace("${sign}", sign));
                }
            }

            logger.debug("生成签名: {}", sign);

        } catch (Exception e) {
            logger.error("签名认证失败", e);
        }
    }

    /**
     * OAuth2.0认证
     */
    private void handleOAuth2Auth(Map<String, String> headers, ThirdPartyConfig config) {
        try {
            String token = getOrRefreshToken(config);
            if (token != null) {
                // 替换Header中的${token}占位符
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getValue().contains("${token}")) {
                        headers.put(entry.getKey(), entry.getValue().replace("${token}", token));
                    }
                }
                logger.debug("使用OAuth2 Token");
            }
        } catch (Exception e) {
            logger.error("OAuth2认证失败", e);
        }
    }

    /**
     * 获取或刷新Token
     */
    private String getOrRefreshToken(ThirdPartyConfig config) {
        String code = config.getCode();
        TokenInfo tokenInfo = tokenCache.get(code);

        // 检查Token是否过期
        if (tokenInfo == null || tokenInfo.isExpired()) {
            // 刷新Token
            tokenInfo = refreshToken(config);
            if (tokenInfo != null) {
                tokenCache.put(code, tokenInfo);
            }
        }

        return tokenInfo != null ? tokenInfo.getAccessToken() : null;
    }

    /**
     * 刷新OAuth2 Token
     */
    private TokenInfo refreshToken(ThirdPartyConfig config) {
        try {
            Map<String, String> authParams = config.getAuthParams();
            if (authParams == null) {
                return null;
            }

            String clientId = authParams.get("clientId");
            String clientSecret = authParams.get("clientSecret");
            String tokenUrl = authParams.get("tokenUrl");
            String grantType = authParams.getOrDefault("grantType", "client_credentials");

            // TODO: 实际调用Token接口获取Token
            // 这里简化处理，返回模拟Token
            logger.info("刷新OAuth2 Token, clientId: {}", clientId);

            // 模拟返回Token
            String accessToken = "mock_token_" + System.currentTimeMillis();
            long expiresIn = 7200; // 2小时

            return new TokenInfo(accessToken, expiresIn);

        } catch (Exception e) {
            logger.error("刷新Token失败", e);
            return null;
        }
    }

    /**
     * 计算签名
     */
    private String calculateSign(String data, String algorithm) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Token信息
     */
    private static class TokenInfo {
        private final String accessToken;
        private final long expiresAt; // 过期时间戳（ms）

        public TokenInfo(String accessToken, long expiresIn) {
            this.accessToken = accessToken;
            this.expiresAt = System.currentTimeMillis() + expiresIn * 1000;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public boolean isExpired() {
            // 提前5分钟刷新
            return System.currentTimeMillis() >= (expiresAt - 300000);
        }
    }
}
