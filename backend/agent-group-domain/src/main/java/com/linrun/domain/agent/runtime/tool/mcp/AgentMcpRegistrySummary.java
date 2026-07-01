package com.linrun.domain.agent.runtime.tool.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentMcpRegistrySummary(int serverCount,
                                         int enabledServerCount,
                                         int disabledServerCount,
                                         int registeredToolCount,
                                         int enabledToolCount,
                                         int cachedServerCount,
                                         int emptyCacheServerCount,
                                         List<String> cachedServerIds,
                                         List<String> serversWithoutCachedTools,
                                         List<String> enabledServersWithoutCachedTools,
                                         Map<String, Integer> transportCounts) {

    public AgentMcpRegistrySummary {
        serverCount = Math.max(0, serverCount);
        enabledServerCount = Math.max(0, enabledServerCount);
        disabledServerCount = Math.max(0, disabledServerCount);
        registeredToolCount = Math.max(0, registeredToolCount);
        enabledToolCount = Math.max(0, enabledToolCount);
        cachedServerCount = Math.max(0, cachedServerCount);
        emptyCacheServerCount = Math.max(0, emptyCacheServerCount);
        cachedServerIds = cachedServerIds == null ? List.of() : List.copyOf(cachedServerIds);
        serversWithoutCachedTools = serversWithoutCachedTools == null
                ? List.of()
                : List.copyOf(serversWithoutCachedTools);
        enabledServersWithoutCachedTools = enabledServersWithoutCachedTools == null
                ? List.of()
                : List.copyOf(enabledServersWithoutCachedTools);
        transportCounts = transportCounts == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(transportCounts));
    }

    public boolean hasEnabledServerWithoutCache() {
        return !enabledServersWithoutCachedTools.isEmpty();
    }

    public boolean hasEnabledTool() {
        return enabledToolCount > 0;
    }
}















