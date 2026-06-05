package com.linrun.domain.academic.runtime.tool.mcp;

import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicMcpRegistryTest {

    @Test
    void shouldRegisterServerCacheToolsAndExposeEnabledDefinitions() {
        AcademicMcpRegistry registry = new AcademicMcpRegistry();
        registry.registerServer(AcademicMcpServerDescriptor.builder("research")
                .name("research tools")
                .endpoint("http://localhost:8090/mcp")
                .build());
        registry.cacheDiscoveredTools("research", List.of(AcademicMcpToolDescriptor.builder("research", "web_fetch")
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
        AcademicMcpRegistry registry = new AcademicMcpRegistry();
        registry.registerServer(AcademicMcpServerDescriptor.builder("image")
                .endpoint("http://localhost:8091/mcp")
                .build());
        registry.cacheDiscoveredTools("image", List.of(AcademicMcpToolDescriptor.builder("image", "generate").build()));

        registry.enableServer("image", false);

        assertTrue(registry.listEnabledTools().isEmpty());
    }

    @Test
    void shouldRejectUnknownServerWhenCachingTools() {
        AcademicMcpRegistry registry = new AcademicMcpRegistry();

        AppException exception = assertThrows(AppException.class,
                () -> registry.cacheDiscoveredTools("missing", List.of()));

        assertEquals("MCP_0102", exception.getCode());
    }

    @Test
    void shouldRestoreServersAndCachedToolsFromSnapshot() {
        AcademicMcpRegistry registry = new AcademicMcpRegistry();
        registry.registerServer(AcademicMcpServerDescriptor.builder("data")
                .name("data tools")
                .endpoint("http://localhost:8092/mcp")
                .transport("sse")
                .enabled(false)
                .metadata(Map.of("headers", Map.of("X-Tenant", "demo")))
                .build());
        registry.cacheDiscoveredTools("data", List.of(AcademicMcpToolDescriptor.builder("data", "chart")
                .description("render chart")
                .inputSchema(Map.of("type", "object", "required", List.of("query")))
                .build()));

        AcademicMcpRegistry restored = new AcademicMcpRegistry().restore(registry.snapshot());

        assertEquals(1, restored.listServers().size());
        assertEquals(false, restored.listServers().getFirst().isEnabled());
        assertEquals("sse", restored.listServers().getFirst().getTransport());
        assertEquals("data.chart", restored.listTools("").getFirst().qualifiedName());
        assertTrue(restored.listEnabledTools().isEmpty());
        assertTrue(restored.lastDiscoveredAt("data").isPresent());
    }
}
