package com.linrun.domain.academic.runtime.tool.common;

import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AcademicToolArguments {

    private AcademicToolArguments() {
    }

    static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    static String defaultText(Object value, String fallback) {
        String text = text(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    static String firstPresent(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = text(value);
        return StringUtils.hasText(text) ? Boolean.parseBoolean(text) : fallback;
    }

    static int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(AcademicToolArguments::text)
                .filter(StringUtils::hasText)
                .toList();
    }

    static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, entryValue) -> {
            if (key instanceof String textKey) {
                result.put(textKey, entryValue);
            }
        });
        return result;
    }

    static List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(AcademicToolArguments::objectMapEntry)
                .filter(entry -> entry != null)
                .toList();
    }

    private static Map<String, Object> objectMapEntry(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return null;
        }
        Map<String, Object> map = objectMap(value);
        return !map.isEmpty() || raw.isEmpty() ? map : null;
    }
}
