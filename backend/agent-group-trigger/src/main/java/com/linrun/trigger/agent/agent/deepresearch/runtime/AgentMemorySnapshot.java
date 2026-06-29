package com.linrun.trigger.agent.agent.deepresearch.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentMemorySnapshot(
        String tenantId,
        String userId,
        String sessionId,
        List<String> shortTerm,
        List<String> taskMemory,
        List<String> longTerm,
        boolean longTermEnabled
) {

    public AgentMemorySnapshot {
        tenantId = safe(tenantId);
        userId = safe(userId);
        sessionId = safe(sessionId);
        shortTerm = copy(shortTerm);
        taskMemory = copy(taskMemory);
        longTerm = longTermEnabled ? copy(longTerm) : List.of();
    }

    public static AgentMemorySnapshot empty(String tenantId, String userId, String sessionId) {
        return new AgentMemorySnapshot(tenantId, userId, sessionId, List.of(), List.of(), List.of(), false);
    }

    public Map<String, Object> evidence() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tenantId", tenantId);
        data.put("userId", userId);
        data.put("sessionId", sessionId);
        data.put("shortTermCount", shortTerm.size());
        data.put("taskMemoryCount", taskMemory.size());
        data.put("longTermCount", longTerm.size());
        data.put("longTermEnabled", longTermEnabled);
        return data;
    }

    private static List<String> copy(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
