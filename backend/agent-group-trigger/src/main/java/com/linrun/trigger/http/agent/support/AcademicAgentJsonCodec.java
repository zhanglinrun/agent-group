package com.linrun.trigger.http.agent.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学术 Agent 链路里通用的 JSON / JsonNode 解析工具，从 AcademicAgentHandler 抽出，
 * 让 Handler 和后续拆分出的会话、文件、能力等 Service 都能复用同一套解析口径。
 */
@Component
public class AcademicAgentJsonCodec {

    private final ObjectMapper objectMapper;

    public AcademicAgentJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String content(JsonNode node) {
        JsonNode content = node.get("content");
        if (content == null || content.isNull()) {
            return "";
        }
        return content.isTextual() ? content.asText() : content.toString();
    }

    public String jsonOrText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node == null ? null : node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            return value.isTextual() ? value.asText("") : value.toString();
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseObject(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Object value = objectMapper.readValue(json, Object.class);
            if (value instanceof Map<?, ?> map) {
                return new LinkedHashMap<>((Map<String, Object>) map);
            }
        } catch (Exception ignored) {
        }
        return Map.of();
    }

    public String toJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper == null ? "{}" : objectMapper.writeValueAsString(data);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    public void putIfPresent(Map<String, Object> data, String key, Object value) {
        if (!StringUtils.hasText(key) || value == null) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return;
        }
        data.put(key, value);
    }

    public String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    public int integer(JsonNode node, String field, int fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isNumber()) {
            return value.asInt(fallback);
        }
        try {
            return Integer.parseInt(value.asText());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public long longValue(JsonNode node, String field, long fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isNumber()) {
            return value.asLong(fallback);
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean booleanValue(JsonNode node, String field, boolean fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        if (value.isBoolean()) {
            return value.asBoolean(fallback);
        }
        String text = value.asText();
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        return "true".equalsIgnoreCase(text)
                || "1".equals(text)
                || "yes".equalsIgnoreCase(text);
    }

    public String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    public String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }
}
