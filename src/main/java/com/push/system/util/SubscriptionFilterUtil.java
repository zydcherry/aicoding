package com.push.system.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.Map;

/**
 * 订阅过滤工具类
 * 用于判断事件数据是否匹配订阅的过滤条件
 */
public class SubscriptionFilterUtil {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionFilterUtil.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 判断事件数据是否匹配过滤条件
     *
     * @param filterConditionJson 过滤条件JSON字符串
     * @param eventDataJson 事件数据JSON字符串
     * @return true-匹配，false-不匹配
     */
    public static boolean matches(String filterConditionJson, String eventDataJson) {
        // 如果没有过滤条件，默认匹配
        if (filterConditionJson == null || filterConditionJson.trim().isEmpty()) {
            return true;
        }

        try {
            JsonNode filterNode = objectMapper.readTree(filterConditionJson);
            JsonNode eventNode = objectMapper.readTree(eventDataJson);

            return matchesNode(filterNode, eventNode);

        } catch (Exception e) {
            logger.error("解析过滤条件失败", e);
            // 解析失败时默认匹配，避免过滤条件配置错误导致无法推送
            return true;
        }
    }

    /**
     * 递归匹配节点
     */
    private static boolean matchesNode(JsonNode filterNode, JsonNode eventNode) {
        if (filterNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = filterNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                JsonNode filterValue = entry.getValue();

                // 处理操作符
                if (fieldName.startsWith("$")) {
                    if (!matchesOperator(fieldName, filterValue, eventNode)) {
                        return false;
                    }
                } else {
                    // 普通字段匹配
                    JsonNode eventValue = eventNode.get(fieldName);
                    if (eventValue == null) {
                        return false;
                    }
                    if (!matchesValue(filterValue, eventValue)) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            // 简单值比较
            return filterNode.equals(eventNode);
        }
    }

    /**
     * 匹配操作符
     */
    private static boolean matchesOperator(String operator, JsonNode filterValue, JsonNode eventValue) {
        switch (operator) {
            case "$eq":  // 等于
                return eventValue.equals(filterValue);
            case "$ne":  // 不等于
                return !eventValue.equals(filterValue);
            case "$gt":  // 大于
                return compareNumber(eventValue, filterValue) > 0;
            case "$gte": // 大于等于
                return compareNumber(eventValue, filterValue) >= 0;
            case "$lt":  // 小于
                return compareNumber(eventValue, filterValue) < 0;
            case "$lte": // 小于等于
                return compareNumber(eventValue, filterValue) <= 0;
            case "$in":  // 在数组中
                if (filterValue.isArray()) {
                    for (JsonNode item : filterValue) {
                        if (item.equals(eventValue)) {
                            return true;
                        }
                    }
                }
                return false;
            case "$nin": // 不在数组中
                if (filterValue.isArray()) {
                    for (JsonNode item : filterValue) {
                        if (item.equals(eventValue)) {
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            case "$exists": // 字段存在
                boolean shouldExist = filterValue.asBoolean();
                return (eventValue != null && !eventValue.isNull()) == shouldExist;
            default:
                logger.warn("不支持的操作符: {}", operator);
                return true;
        }
    }

    /**
     * 匹配值
     */
    private static boolean matchesValue(JsonNode filterValue, JsonNode eventValue) {
        if (filterValue.isObject()) {
            // 包含操作符
            return matchesNode(filterValue, eventValue);
        } else if (filterValue.isArray()) {
            // 数组匹配（in操作）
            for (JsonNode item : filterValue) {
                if (item.equals(eventValue)) {
                    return true;
                }
            }
            return false;
        } else {
            // 简单值匹配
            return filterValue.equals(eventValue);
        }
    }

    /**
     * 比较数字
     */
    private static int compareNumber(JsonNode value1, JsonNode value2) {
        if (value1.isNumber() && value2.isNumber()) {
            double d1 = value1.asDouble();
            double d2 = value2.asDouble();
            return Double.compare(d1, d2);
        }
        return 0;
    }
}
