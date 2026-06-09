package com.linrun.domain.academic.runtime.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AcademicToolSchemaNormalizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private AcademicToolSchemaNormalizer() {
    }

    public static Map<String, Object> normalize(Map<String, Object> rawSchema) {
        Map<String, Object> schema = deepCopy(rawSchema);
        sanitize(schema, true);
        return schema;
    }

    public static List<String> requiredArguments(Map<String, Object> inputSchema) {
        Object required = inputSchema == null ? null : inputSchema.get("required");
        if (!(required instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private static Map<String, Object> deepCopy(Map<String, Object> rawSchema) {
        if (rawSchema == null || rawSchema.isEmpty()) {
            return emptyObjectSchema();
        }
        try {
            Map<String, Object> copied = OBJECT_MAPPER.convertValue(rawSchema, MAP_TYPE);
            return copied == null ? emptyObjectSchema() : copied;
        } catch (IllegalArgumentException ignored) {
            return emptyObjectSchema();
        }
    }

    @SuppressWarnings("unchecked")
    private static void sanitize(Object node, boolean root) {
        if (node instanceof Map<?, ?> rawMap) {
            Map<String, Object> schema = (Map<String, Object>) rawMap;
            schema.remove("$schema");
            schema.remove("additionalProperties");

            Object type = schema.get("type");
            boolean objectSchema = root
                    || "object".equals(type)
                    || (type == null && (schema.containsKey("properties") || schema.containsKey("required")));
            if (objectSchema) {
                if (!Objects.equals(type, "object")) {
                    schema.put("type", "object");
                }
                if (!(schema.get("properties") instanceof Map<?, ?>)) {
                    schema.put("properties", new LinkedHashMap<String, Object>());
                }
                if (!(schema.get("required") instanceof List<?>)) {
                    schema.put("required", new ArrayList<String>());
                }
            }

            for (Object value : new ArrayList<>(schema.values())) {
                sanitize(value, false);
            }
            return;
        }
        if (node instanceof List<?> list) {
            for (Object value : list) {
                sanitize(value, false);
            }
        }
    }

    private static Map<String, Object> emptyObjectSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", new LinkedHashMap<String, Object>());
        schema.put("required", new ArrayList<String>());
        return schema;
    }
}















