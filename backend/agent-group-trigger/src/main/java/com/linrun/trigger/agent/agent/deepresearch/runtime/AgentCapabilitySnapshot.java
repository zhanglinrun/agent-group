package com.linrun.trigger.agent.agent.deepresearch.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentCapabilitySnapshot(
        String runtime,
        String mode,
        int skillCount,
        int toolCount,
        int capabilityCount,
        Map<String, Object> memory,
        Map<String, Object> roleContext
) {

    public AgentCapabilitySnapshot {
        runtime = runtime == null ? "" : runtime.trim();
        mode = mode == null ? "" : mode.trim();
        memory = memory == null ? Map.of() : Map.copyOf(memory);
        roleContext = roleContext == null ? Map.of() : Map.copyOf(roleContext);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runtime", runtime);
        data.put("mode", mode);
        data.put("skillCount", skillCount);
        data.put("toolCount", toolCount);
        data.put("capabilityCount", capabilityCount);
        data.put("memory", memory);
        data.put("roleContext", roleContext);
        data.put("runtimeEvidence", List.of(
                "mode_routes_execution",
                "context_is_role_scoped",
                "skills_are_runtime_checked",
                "tools_are_runtime_registered",
                "memory_is_user_scoped"));
        return data;
    }
}
