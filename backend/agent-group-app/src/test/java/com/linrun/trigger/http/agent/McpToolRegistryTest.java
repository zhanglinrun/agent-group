package com.linrun.trigger.http.agent;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpToolRegistryTest {

    @Test
    void shouldExposeMcpDefinitionAndCallThroughRuntimeRegistry() {
        McpToolRegistry registry = new McpToolRegistry();
        registry.register(tool("json_repair"), arguments -> Map.of("text", arguments.get("text")));

        assertEquals(List.of("json_repair"), registry.toolNames());
        assertEquals("json_repair", registry.listTools().getFirst().get("name"));
        assertEquals("{}", registry.callTool("json_repair", Map.of("text", "{}")).get("text"));
    }

    @Test
    void shouldValidateRequiredArgumentsBeforeCallingHandler() {
        McpToolRegistry registry = new McpToolRegistry();
        registry.register(tool("query_route"), arguments -> Map.of("route", "knowledge_base"));

        AppException exception = assertThrows(AppException.class,
                () -> registry.callTool("query_route", Map.of()));

        assertEquals("TOOL_0004", exception.getCode());
    }

    @Test
    void shouldExposeRegisteredToolsAsCollectionView() {
        McpToolRegistry registry = new McpToolRegistry();
        registry.register(tool("json_repair"), arguments -> Map.of("text", arguments.get("text")));

        var collection = registry.asToolCollection("mcp_bridge", List.of("json_repair"));

        assertEquals(List.of("json_repair"), collection.toolNames());
        assertEquals("{}", collection.call(AcademicToolCallCommand.builder("json_repair")
                .arguments(Map.of("text", "{}"))
                .build()).getResult().get("text"));
    }

    private Map<String, Object> tool(String name) {
        return Map.of(
                "name", name,
                "description", "test mcp tool",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of("text", Map.of("type", "string")),
                        "required", List.of("text")));
    }
}
