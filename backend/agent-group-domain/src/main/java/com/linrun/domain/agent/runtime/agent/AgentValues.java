package com.linrun.domain.agent.runtime.agent;

import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

final class AgentValues {

    private AgentValues() {
    }

    static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static Map<String, Object> copyMap(Map<String, Object> values) {
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
}
