package com.linrun.domain.academic.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record AcademicReActObservation(boolean success,
                                       String content,
                                       Map<String, Object> metadata) {

    public AcademicReActObservation {
        content = safe(content);
        metadata = copyMap(metadata);
    }

    public static AcademicReActObservation success(String content) {
        return success(content, Map.of());
    }

    public static AcademicReActObservation success(String content, Map<String, Object> metadata) {
        return new AcademicReActObservation(true, content, metadata);
    }

    public static AcademicReActObservation failed(String content) {
        return failed(content, Map.of());
    }

    public static AcademicReActObservation failed(String content, Map<String, Object> metadata) {
        return new AcademicReActObservation(false, content, metadata);
    }

    private static Map<String, Object> copyMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (StringUtils.hasText(key)) {
                result.put(key.trim(), value == null ? "" : value);
            }
        });
        return Collections.unmodifiableMap(result);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}















