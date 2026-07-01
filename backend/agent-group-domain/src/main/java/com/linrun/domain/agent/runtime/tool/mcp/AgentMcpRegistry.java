package com.linrun.domain.agent.runtime.tool.mcp;

import com.linrun.domain.agent.runtime.tool.AgentToolDefinition;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AgentMcpRegistry {

    private final Map<String, AgentMcpServerDescriptor> servers = new LinkedHashMap<>();
    private final Map<String, Map<String, AgentMcpToolDescriptor>> toolsByServer = new LinkedHashMap<>();
    private final Map<String, LocalDateTime> discoveredAtByServer = new LinkedHashMap<>();

    public synchronized AgentMcpRegistry registerServer(AgentMcpServerDescriptor server) {
        if (server == null) {
            throw new IllegalArgumentException("mcp server cannot be null");
        }
        servers.put(server.getServerId(), server);
        toolsByServer.putIfAbsent(server.getServerId(), new LinkedHashMap<>());
        return this;
    }

    public synchronized AgentMcpRegistry enableServer(String serverId, boolean enabled) {
        AgentMcpServerDescriptor server = requireServer(serverId);
        servers.put(server.getServerId(), server.withEnabled(enabled));
        return this;
    }

    public synchronized AgentMcpRegistry cacheDiscoveredTools(String serverId,
                                                                 List<AgentMcpToolDescriptor> tools) {
        return cacheDiscoveredTools(serverId, tools, LocalDateTime.now());
    }

    public synchronized AgentMcpRegistry cacheDiscoveredTools(String serverId,
                                                                 List<AgentMcpToolDescriptor> tools,
                                                                 LocalDateTime discoveredAt) {
        requireServer(serverId);
        Map<String, AgentMcpToolDescriptor> cached = new LinkedHashMap<>();
        if (tools != null) {
            for (AgentMcpToolDescriptor tool : tools) {
                if (tool != null && serverId.equals(tool.getServerId())) {
                    cached.put(tool.getToolName(), tool);
                }
            }
        }
        toolsByServer.put(serverId, cached);
        discoveredAtByServer.put(serverId, discoveredAt == null ? LocalDateTime.now() : discoveredAt);
        return this;
    }

    public synchronized Optional<AgentMcpToolDescriptor> findTool(String qualifiedName) {
        if (!StringUtils.hasText(qualifiedName) || !qualifiedName.contains(".")) {
            return Optional.empty();
        }
        int splitAt = qualifiedName.indexOf('.');
        String serverId = qualifiedName.substring(0, splitAt);
        String toolName = qualifiedName.substring(splitAt + 1);
        return Optional.ofNullable(toolsByServer.getOrDefault(serverId, Map.of()).get(toolName));
    }

    public synchronized List<AgentMcpServerDescriptor> listServers() {
        return new ArrayList<>(servers.values());
    }

    public synchronized List<AgentMcpToolDescriptor> listTools(String serverId) {
        if (!StringUtils.hasText(serverId)) {
            return toolsByServer.values().stream()
                    .flatMap(tools -> tools.values().stream())
                    .toList();
        }
        return new ArrayList<>(toolsByServer.getOrDefault(serverId, Map.of()).values());
    }

    public synchronized List<AgentMcpToolDescriptor> listEnabledTools() {
        return toolsByServer.entrySet().stream()
                .filter(entry -> servers.get(entry.getKey()) != null && servers.get(entry.getKey()).isEnabled())
                .flatMap(entry -> entry.getValue().values().stream())
                .filter(AgentMcpToolDescriptor::isEnabled)
                .toList();
    }

    public synchronized List<AgentToolDefinition> listEnabledToolDefinitions() {
        return listEnabledTools().stream()
                .map(AgentMcpToolDescriptor::toToolDefinition)
                .toList();
    }

    public synchronized AgentMcpRegistrySummary summary() {
        List<String> cachedServerIds = new ArrayList<>();
        List<String> serversWithoutCachedTools = new ArrayList<>();
        List<String> enabledServersWithoutCachedTools = new ArrayList<>();
        Map<String, Integer> transportCounts = new LinkedHashMap<>();
        int enabledServerCount = 0;
        int registeredToolCount = 0;
        int enabledToolCount = 0;

        for (AgentMcpServerDescriptor server : servers.values()) {
            if (server.isEnabled()) {
                enabledServerCount++;
            }
            String transport = StringUtils.hasText(server.getTransport()) ? server.getTransport().trim() : "unknown";
            transportCounts.put(transport, transportCounts.getOrDefault(transport, 0) + 1);

            List<AgentMcpToolDescriptor> tools = new ArrayList<>(
                    toolsByServer.getOrDefault(server.getServerId(), Map.of()).values());
            registeredToolCount += tools.size();
            if (server.isEnabled()) {
                enabledToolCount += (int) tools.stream().filter(AgentMcpToolDescriptor::isEnabled).count();
            }
            if (tools.isEmpty()) {
                serversWithoutCachedTools.add(server.getServerId());
                if (server.isEnabled()) {
                    enabledServersWithoutCachedTools.add(server.getServerId());
                }
            } else {
                cachedServerIds.add(server.getServerId());
            }
        }

        return new AgentMcpRegistrySummary(
                servers.size(),
                enabledServerCount,
                Math.max(0, servers.size() - enabledServerCount),
                registeredToolCount,
                enabledToolCount,
                cachedServerIds.size(),
                serversWithoutCachedTools.size(),
                cachedServerIds,
                serversWithoutCachedTools,
                enabledServersWithoutCachedTools,
                transportCounts);
    }

    public synchronized Optional<LocalDateTime> lastDiscoveredAt(String serverId) {
        return Optional.ofNullable(discoveredAtByServer.get(serverId));
    }

    public synchronized AgentMcpCacheStatus cacheStatus(String serverId, Duration cacheTtl) {
        return cacheStatus(serverId, cacheTtl, LocalDateTime.now());
    }

    public synchronized AgentMcpCacheStatus cacheStatus(String serverId,
                                                           Duration cacheTtl,
                                                           LocalDateTime now) {
        AgentMcpServerDescriptor server = requireServer(serverId);
        return buildCacheStatus(server, cacheTtl, now);
    }

    public synchronized List<AgentMcpCacheStatus> cacheStatuses(Duration cacheTtl) {
        return cacheStatuses(cacheTtl, LocalDateTime.now());
    }

    public synchronized List<AgentMcpCacheStatus> cacheStatuses(Duration cacheTtl, LocalDateTime now) {
        return servers.values().stream()
                .map(server -> buildCacheStatus(server, cacheTtl, now))
                .toList();
    }

    public synchronized Snapshot snapshot() {
        List<ServerState> serverStates = servers.values().stream()
                .map(server -> new ServerState(
                        server.getServerId(),
                        server.getName(),
                        server.getEndpoint(),
                        server.getTransport(),
                        server.isEnabled(),
                        server.getMetadata()))
                .toList();
        Map<String, List<ToolState>> toolStates = new LinkedHashMap<>();
        toolsByServer.forEach((serverId, tools) -> toolStates.put(serverId, tools.values().stream()
                .map(tool -> new ToolState(
                        tool.getServerId(),
                        tool.getToolName(),
                        tool.getDescription(),
                        tool.getInputSchema(),
                        tool.isEnabled(),
                        tool.getDiscoveredAt().toString()))
                .toList()));
        Map<String, String> discoveredStates = new LinkedHashMap<>();
        discoveredAtByServer.forEach((serverId, discoveredAt) -> {
            if (discoveredAt != null) {
                discoveredStates.put(serverId, discoveredAt.toString());
            }
        });
        return new Snapshot(serverStates, toolStates, discoveredStates);
    }

    public synchronized AgentMcpRegistry restore(Snapshot snapshot) {
        servers.clear();
        toolsByServer.clear();
        discoveredAtByServer.clear();
        if (snapshot == null) {
            return this;
        }
        for (ServerState server : safeList(snapshot.servers())) {
            if (!StringUtils.hasText(server.serverId()) || !StringUtils.hasText(server.endpoint())) {
                continue;
            }
            registerServer(AgentMcpServerDescriptor.builder(server.serverId())
                    .name(server.name())
                    .endpoint(server.endpoint())
                    .transport(server.transport())
                    .enabled(server.enabled())
                    .metadata(server.metadata())
                    .build());
        }
        safeMap(snapshot.toolsByServer()).forEach((serverId, tools) -> {
            if (!servers.containsKey(serverId)) {
                return;
            }
            List<AgentMcpToolDescriptor> restoredTools = safeList(tools).stream()
                    .filter(tool -> tool != null && StringUtils.hasText(tool.toolName()))
                    .map(tool -> AgentMcpToolDescriptor.builder(serverId, tool.toolName())
                            .description(tool.description())
                            .inputSchema(tool.inputSchema())
                            .enabled(tool.enabled())
                            .discoveredAt(parseTime(tool.discoveredAt()))
                            .build())
                    .toList();
            cacheDiscoveredTools(serverId, restoredTools);
        });
        safeMap(snapshot.discoveredAtByServer()).forEach((serverId, discoveredAt) -> {
            LocalDateTime time = parseTime(discoveredAt);
            if (time != null && servers.containsKey(serverId)) {
                discoveredAtByServer.put(serverId, time);
            }
        });
        return this;
    }

    private AgentMcpCacheStatus buildCacheStatus(AgentMcpServerDescriptor server,
                                                    Duration cacheTtl,
                                                    LocalDateTime now) {
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        LocalDateTime discoveredAt = discoveredAtByServer.get(server.getServerId());
        long cacheAgeSeconds = discoveredAt == null
                ? 0
                : Math.max(0L, Duration.between(discoveredAt, effectiveNow).toSeconds());
        int toolCount = toolsByServer.getOrDefault(server.getServerId(), Map.of()).size();
        Long ttlSeconds = cacheTtl == null || cacheTtl.isNegative() ? null : cacheTtl.toSeconds();
        return new AgentMcpCacheStatus(server.getServerId(), server.isEnabled(), toolCount,
                discoveredAt, cacheAgeSeconds, ttlSeconds,
                cacheStatus(server, discoveredAt, cacheAgeSeconds, cacheTtl));
    }

    private String cacheStatus(AgentMcpServerDescriptor server,
                               LocalDateTime discoveredAt,
                               long cacheAgeSeconds,
                               Duration cacheTtl) {
        if (!server.isEnabled()) {
            return AgentMcpCacheStatus.STATUS_DISABLED;
        }
        if (discoveredAt == null) {
            return AgentMcpCacheStatus.STATUS_EMPTY;
        }
        if (cacheTtl == null || cacheTtl.isNegative()) {
            return AgentMcpCacheStatus.STATUS_UNBOUNDED;
        }
        if (cacheAgeSeconds > cacheTtl.toSeconds()) {
            return AgentMcpCacheStatus.STATUS_EXPIRED;
        }
        return AgentMcpCacheStatus.STATUS_FRESH;
    }

    private AgentMcpServerDescriptor requireServer(String serverId) {
        if (!StringUtils.hasText(serverId)) {
            throw new AppException("MCP_0101", "mcp server id cannot be blank");
        }
        AgentMcpServerDescriptor server = servers.get(serverId);
        if (server == null) {
            throw new AppException("MCP_0102", "unknown mcp server: " + serverId);
        }
        return server;
    }

    private static LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static <K, V> Map<K, V> safeMap(Map<K, V> values) {
        return values == null ? Map.of() : values;
    }

    public record Snapshot(List<ServerState> servers,
                           Map<String, List<ToolState>> toolsByServer,
                           Map<String, String> discoveredAtByServer) {
    }

    public record ServerState(String serverId,
                              String name,
                              String endpoint,
                              String transport,
                              boolean enabled,
                              Map<String, Object> metadata) {
    }

    public record ToolState(String serverId,
                            String toolName,
                            String description,
                            Map<String, Object> inputSchema,
                            boolean enabled,
                            String discoveredAt) {
    }
}















