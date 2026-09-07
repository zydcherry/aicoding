package com.push.system.http;

import com.push.system.model.PushMessage;
import com.push.system.model.ThirdPartyConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板引擎
 * 负责替换配置模板中的变量
 */
@Component
public class TemplateEngine {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /**
     * 渲染模板，替换其中的变量
     */
    public String render(String template, PushMessage message, ThirdPartyConfig config) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        // 构建变量上下文
        Map<String, Object> context = buildContext(message, config);

        // 替换变量
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String variableName = matcher.group(1);
            Object value = resolveVariable(variableName, context);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value != null ? value.toString() : ""));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 构建变量上下文
     */
    private Map<String, Object> buildContext(PushMessage message, ThirdPartyConfig config) {
        Map<String, Object> context = new HashMap<>();

        // message相关变量
        context.put("message.id", message.getId());
        context.put("message.bizId", message.getBizId());
        context.put("message.content", message.getContent());
        context.put("message.messageType", message.getMessageType());
        context.put("message.thirdPartyCode", message.getThirdPartyCode());

        // 系统变量
        context.put("timestamp", System.currentTimeMillis());
        context.put("timestampSeconds", Instant.now().getEpochSecond());

        // config相关变量（从authParams中获取）
        if (config.getAuthParams() != null) {
            for (Map.Entry<String, String> entry : config.getAuthParams().entrySet()) {
                context.put("config." + entry.getKey(), entry.getValue());
            }
        }

        return context;
    }

    /**
     * 解析变量值
     */
    private Object resolveVariable(String variableName, Map<String, Object> context) {
        // 直接查找
        if (context.containsKey(variableName)) {
            return context.get(variableName);
        }

        // 特殊变量处理
        if ("sign".equals(variableName)) {
            // 签名会由AuthHandler单独处理
            return "${sign}";
        }

        if ("token".equals(variableName)) {
            // Token会由AuthHandler单独处理
            return "${token}";
        }

        return "";
    }
}
