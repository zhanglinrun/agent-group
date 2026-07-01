package com.linrun.domain.agent.runtime.tool.mcp;

import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMcpRegistryTest {

    @Test
    void shouldRegisterServerCacheToolsAndExposeEnabledDefinitions() {
        AgentMcpRegistry registry = new AgentMcpRegistry();
        registry.registerServer(AgentMcpServerDescriptor.builder("research")
                .name("research tools")
                .endpoint("http://localhost:8090/mcp")
                .build());
        registry.cacheDiscoveredTools("research", List.of(AgentMcpToolDescriptor.builder("research", "web_fetch")
                .description("fetch web page")
                .inputSchema(Map.of("type", "object",
                        "properties", Map.of("url", Map.of("type", "string")),
                        "required", List.of("url")))
                .build()));

        assertEquals(1, registry.listEnabledTools().size());
        assertEquals("research.web_fetch", registry.listEnabledToolDefinitions().getFirst().getName());
        assertEquals(List.of("url"), registry.listEnabledToolDefinitions().getFirst().getRequiredArguments());
        assertTrue(registry.findTool("research.web_fetch").isPresent());
        assertTrue(registry.lastDiscoveredAt("research").isPresent());
    }

    @Test
    void shouldHideToolsWhenServerDisabled() {
        AgentMcpRegistry registry = new AgentMcpRegistry();
        registry.registerServer(AgentMcpServerDescriptor.builder("image")
                .endpoint("http://localhost:8091/mcp")
                .build());
        registry.cacheDiscoveredTools("image", List.of(AgentMcpToolDescriptor.builder("image", "generate").build()));

        registry.enableServer("image", false);

        assertTrue(registry.listEnabledTools().isEmpty());
    }

    @Test
    void shouldRejectUnknownServerWhenCachingTools() {
        AgentMcpRegistry registry = new AgentMcpRegistry();

        AppException exception = assertThrows(AppException.class,
                () -> registry.cacheDiscoveredTools("missing", List.of()));

        assertEquals("MCP_0102", exception.getCode());
    }

    @Test
    void shouldRestoreServersAndCachedToolsFromSnapshot() {
        AgentMcpRegistry registry = new AgentMcpRegistry();
        registry.registerServer(AgentMcpServerDescriptor.builder("data")
                .name("data tools")
                .endpoint("http://localhost:8092/mcp")
                .transport("sse")
                .enabled(false)
                .metadata(Map.of("headers", Map.of("X-Tenant", "demo")))
                .build());
        registry.cacheDiscoveredTools("data", List.of(AgentMcpToolDescriptor.builder("data", "chart")
                .description("render chart")
                .inputSchema(Map.of("type", "object", "required", List.of("query")))
                .build()));

        AgentMcpRegistry restored = new AgentMcpRegistry().restore(registry.snapshot());

        assertEquals(1, restored.listServers().size());
        assertEquals(false, restored.listServers().getFirst().isEnabled());
        assertEquals("sse", restored.listServers().getFirst().getTransport());
        assertEquals("data.chart", restored.listTools("").getFirst().qualifiedName());
        assertTrue(restored.listEnabledTools().isEmpty());
        assertTrue(restored.lastDiscoveredAt("data").isPresent());
    }

    @Test
    void shouldReportToolCacheFreshnessAndRefreshRequirement() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 5, 12, 0);
        AgentMcpRegistry registry = new AgentMcpRegistry();
        registry.registerServer(AgentMcpServerDescriptor.builder("research")
                .endpoint("http://localhost:8090/mcp")
                .build());

        AgentMcpCacheStatus empty = registry.cacheStatus("research", Duration.ofMinutes(30), now);
        assertEquals(AgentMcpCacheStatus.STATUS_EMPTY, empty.cacheStatus());
        assertTrue(empty.refreshRequired());

        registry.cacheDiscoveredTools("research",
                List.of(AgentMcpToolDescriptor.builder("research", "web_fetch").build()),
                now.minusMinutes(5));
        AgentMcpCacheStatus fresh = registry.cacheStatus("research", Duration.ofMinutes(30), now);
        assertEquals(AgentMcpCacheStatus.STATUS_FRESH, fresh.cacheStatus());
        assertEquals(300, fresh.cacheAgeSeconds());
        assertEquals(1, fresh.toolCount());
        assertFalse(fresh.refreshRequired());

        AgentMcpCacheStatus expired = registry.cacheStatus("research", Duration.ofMinutes(1), now);
        assertEquals(AgentMcpCacheStatus.STATUS_EXPIRED, expired.cacheStatus());
        assertTrue(expired.refreshRequired());

        AgentMcpCacheStatus unbounded = registry.cacheStatus("research", null, now);
        assertEquals(AgentMcpCacheStatus.STATUS_UNBOUNDED, unbounded.cacheStatus());
        assertFalse(unbounded.refreshRequired());

        registry.enableServer("research", false);
        AgentMcpCacheStatus disabled = registry.cacheStatus("research", Duration.ofMinutes(1), now);
        assertEquals(AgentMcpCacheStatus.STATUS_DISABLED, disabled.cacheStatus());
        assertFalse(disabled.refreshRequired());
    }

    @Test
    void shouldSummarizeServersToolsCacheAndTransportCoverage() {
        AgentMcpRegistry registry = new AgentMcpRegistry();
        registry.registerServer(AgentMcpServerDescriptor.builder("research")
                .endpoint("http://localhost:8090/mcp")
                .build());
        registry.registerServer(AgentMcpServerDescriptor.builder("local")
                .endpoint("stdio://local")
                .transport("stdio")
                .enabled(false)
                .build());
        registry.registerServer(AgentMcpServerDescriptor.builder("preview")
                .endpoint("http://localhost:8092/mcp")
                .build());
        registry.cacheDiscoveredTools("research", List.of(
                AgentMcpToolDescriptor.builder("research", "web_fetch").build(),
                AgentMcpToolDescriptor.builder("research", "disabled_search").enabled(false).build()));
        registry.cacheDiscoveredTools("local", List.of(
                AgentMcpToolDescriptor.builder("local", "shell").build()));

        AgentMcpRegistrySummary summary = registry.summary();

        assertEquals(3, summary.serverCount());
        assertEquals(2, summary.enabledServerCount());
        assertEquals(1, summary.disabledServerCount());
        assertEquals(3, summary.registeredToolCount());
        assertEquals(1, summary.enabledToolCount());
        assertEquals(2, summary.cachedServerCount());
        assertEquals(1, summary.emptyCacheServerCount());
        assertEquals(List.of("research", "local"), summary.cachedServerIds());
        assertEquals(List.of("preview"), summary.serversWithoutCachedTools());
        assertEquals(List.of("preview"), summary.enabledServersWithoutCachedTools());
        assertEquals(2, summary.transportCounts().get("streamable_http"));
        assertEquals(1, summary.transportCounts().get("stdio"));
        assertTrue(summary.hasEnabledServerWithoutCache());
        assertTrue(summary.hasEnabledTool());
    }
}















