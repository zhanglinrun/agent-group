package com.linrun.trigger.agent.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Agent 模块统一的 JSON 工具，基于 Jackson。
 * 取代 fastjson2 的混用，避免同一进程内两套序列化行为不一致。
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private JsonUtils() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("serialize json failed: " + e.getMessage(), e);
        }
    }

    public static JsonNode parse(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("parse json failed: " + e.getMessage(), e);
        }
    }

    public static <T> T parseValue(String json, Class<T> type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("parse json to " + type.getName() + " failed: " + e.getMessage(), e);
        }
    }

    public static <T> List<T> parseList(String json, Class<T> elementType) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json,
                    MAPPER.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            throw new RuntimeException("parse json list to " + elementType.getName() + " failed: " + e.getMessage(), e);
        }
    }

    public static <T> T parseValue(String json, TypeReference<T> type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("parse json failed: " + e.getMessage(), e);
        }
    }

    public static int arraySize(String json) {
        JsonNode node = parse(json);
        if (node == null || !node.isArray()) {
            return 0;
        }
        return node.size();
    }

    public static ObjectNode objectNode() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode arrayNode() {
        return MAPPER.createArrayNode();
    }
}
