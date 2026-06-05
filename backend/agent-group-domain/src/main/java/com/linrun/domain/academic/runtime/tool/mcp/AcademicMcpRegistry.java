package com.linrun.domain.academic.runtime.tool.mcp;

import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AcademicMcpRegistry {

    private final Map<String, AcademicMcpServerDescriptor> servers = new LinkedHashMap<>();
    private final Map<String, Map<String, AcademicMcpToolDescriptor>> toolsByServer = new LinkedHashMap<>();
    private final Map<String, LocalDateTime> discoveredAtByServer = new LinkedHashMap<>();

    public synchronized AcademicMcpRegistry registerServer(AcademicMcpServerDescriptor server) {
        if (server == null) {
            throw new IllegalArgumentException("mcp server cannot be null");
        }
        servers.put(server.getServerId(), server);
        toolsByServer.putIfAbsent(server.getServerId(), new LinkedHashMap<>());
        return this;
    }

    public synchronized AcademicMcpRegistry enableServer(String serverId, boolean enabled) {
        AcademicMcpServerDescriptor server = requireServer(serverId);
        servers.put(server.getServerId(), server.withEnabled(enabled));
        return this;
    }

    public synchronized AcademicMcpRegistry cacheDiscoveredTools(String serverId,
                                                                 List<AcademicMcpToolDescriptor> tools) {
        return cacheDiscoveredTools(serverId, tools, LocalDateTime.now());
    }

    public synchronized AcademicMcpRegistry cacheDiscoveredTools(String serverId,
                                                                 List<AcademicMcpToolDescriptor> tools,
                                                                 LocalDateTime discoveredAt) {
        requireServer(serverId);
        Map<String, AcademicMcpToolDescriptor> cached = new LinkedHashMap<>();
        if (tools != null) {
            for (AcademicMcpToolDescriptor tool : tools) {
                if (tool != null && serverId.equals(tool.getServerId())) {
                    cached.put(tool.getToolName(), tool);
                }
            }
        }
        toolsByServer.put(serverId, cached);
        discoveredAtByServer.put(serverId, discoveredAt == null ? LocalDateTime.now() : discoveredAt);
        return this;
    }

    public synchronized Optional<AcademicMcpToolDescriptor> findTool(String qualifiedName) {
        if (!StringUtils.hasText(qualifiedName) || !qualifiedName.contains(".")) {
            return Optional.empty();
        }
        int splitAt = qualifiedName.indexOf('.');
        String serverId = qualifiedName.substring(0, splitAt);
        String toolName = qualifiedName.substring(splitAt + 1);
        return Optional.ofNullable(toolsByServer.getOrDefault(serverId, Map.of()).get(toolName));
    }

    public synchronized List<AcademicMcpServerDescriptor> listServers() {
        return new ArrayList<>(servers.values());
    }

    public synchronized List<AcademicMcpToolDescriptor> listTools(String serverId) {
        if (!StringUtils.hasText(serverId)) {
            return toolsByServer.values().stream()
                    .flatMap(tools -> tools.values().stream())
                    .toList();
        }
        return new ArrayList<>(toolsByServer.getOrDefault(serverId, Map.of()).values());
    }

    public synchronized List<AcademicMcpToolDescriptor> listEnabledTools() {
        return toolsByServer.entrySet().stream()
                .filter(entry -> servers.get(entry.getKey()) != null && servers.get(entry.getKey()).isEnabled())
                .flatMap(entry -> entry.getValue().values().stream())
                .filter(AcademicMcpToolDescriptor::isEnabled)
                .toList();
    }

    public synchronized List<AcademicToolDefinition> listEnabledToolDefinitions() {
        return listEnabledTools().stream()
                .map(AcademicMcpToolDescriptor::toToolDefinition)
                .toList();
    }

    public synchronized AcademicMcpRegistrySummary summary() {
        List<String> cachedServerIds = new ArrayList<>();
        List<String> serversWithoutCachedTools = new ArrayList<>();
        List<String> enabledServersWithoutCachedTools = new ArrayList<>();
        Map<String, Integer> transportCounts = new LinkedHashMap<>();
        int enabledServerCount = 0;
        int registeredToolCount = 0;
        int enabledToolCount = 0;

        for (AcademicMcpServerDescriptor server : servers.values()) {
            if (server.isEnabled()) {
                enabledServerCount++;
            }
            String transport = StringUtils.hasText(server.getTransport()) ? server.getTransport().trim() : "unknown";
            transportCounts.put(transport, transportCounts.getOrDefault(transport, 0) + 1);

            List<AcademicMcpToolDescriptor> tools = new ArrayList<>(
                    toolsByServer.getOrDefault(server.getServerId(), Map.of()).values());
            registeredToolCount += tools.size();
            if (server.isEnabled()) {
                enabledToolCount += (int) tools.stream().filter(AcademicMcpToolDescriptor::isEnabled).count();
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

        return new AcademicMcpRegistrySummary(
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

    public synchronized AcademicMcpCacheStatus cacheStatus(String serverId, Duration cacheTtl) {
        return cacheStatus(serverId, cacheTtl, LocalDateTime.now());
    }

    public synchronized AcademicMcpCacheStatus cacheStatus(String serverId,
                                                           Duration cacheTtl,
                                                           LocalDateTime now) {
        AcademicMcpServerDescriptor server = requireServer(serverId);
        return buildCacheStatus(server, cacheTtl, now);
    }

    public synchronized List<AcademicMcpCacheStatus> cacheStatuses(Duration cacheTtl) {
        return cacheStatuses(cacheTtl, LocalDateTime.now());
    }

    public synchronized List<AcademicMcpCacheStatus> cacheStatuses(Duration cacheTtl, LocalDateTime now) {
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

    public synchronized AcademicMcpRegistry restore(Snapshot snapshot) {
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
            registerServer(AcademicMcpServerDescriptor.builder(server.serverId())
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
            List<AcademicMcpToolDescriptor> restoredTools = safeList(tools).stream()
                    .filter(tool -> tool != null && StringUtils.hasText(tool.toolName()))
                    .map(tool -> AcademicMcpToolDescriptor.builder(serverId, tool.toolName())
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

    private AcademicMcpCacheStatus buildCacheStatus(AcademicMcpServerDescriptor server,
                                                    Duration cacheTtl,
                                                    LocalDateTime now) {
        LocalDateTime effectiveNow = now == null ? LocalDateTime.now() : now;
        LocalDateTime discoveredAt = discoveredAtByServer.get(server.getServerId());
        long cacheAgeSeconds = discoveredAt == null
                ? 0
                : Math.max(0L, Duration.between(discoveredAt, effectiveNow).toSeconds());
        int toolCount = toolsByServer.getOrDefault(server.getServerId(), Map.of()).size();
        Long ttlSeconds = cacheTtl == null || cacheTtl.isNegative() ? null : cacheTtl.toSeconds();
        return new AcademicMcpCacheStatus(server.getServerId(), server.isEnabled(), toolCount,
                discoveredAt, cacheAgeSeconds, ttlSeconds,
                cacheStatus(server, discoveredAt, cacheAgeSeconds, cacheTtl));
    }

    private String cacheStatus(AcademicMcpServerDescriptor server,
                               LocalDateTime discoveredAt,
                               long cacheAgeSeconds,
                               Duration cacheTtl) {
        if (!server.isEnabled()) {
            return AcademicMcpCacheStatus.STATUS_DISABLED;
        }
        if (discoveredAt == null) {
            return AcademicMcpCacheStatus.STATUS_EMPTY;
        }
        if (cacheTtl == null || cacheTtl.isNegative()) {
            return AcademicMcpCacheStatus.STATUS_UNBOUNDED;
        }
        if (cacheAgeSeconds > cacheTtl.toSeconds()) {
            return AcademicMcpCacheStatus.STATUS_EXPIRED;
        }
        return AcademicMcpCacheStatus.STATUS_FRESH;
    }

    private AcademicMcpServerDescriptor requireServer(String serverId) {
        if (!StringUtils.hasText(serverId)) {
            throw new AppException("MCP_0101", "mcp server id cannot be blank");
        }
        AcademicMcpServerDescriptor server = servers.get(serverId);
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
