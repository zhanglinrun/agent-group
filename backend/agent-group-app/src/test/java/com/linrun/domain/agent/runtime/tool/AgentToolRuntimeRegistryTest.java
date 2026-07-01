package com.linrun.domain.agent.runtime.tool;

import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.output.AgentToolStructuredOutput;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolRuntimeRegistryTest {

    @Test
    void shouldRegisterListAndCallTool() {
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.register(definition("literature_search", true), command ->
                Map.of("query", command.getArguments().get("query"), "hitCount", 3));

        AgentToolCallResult result = registry.call(AgentToolCallCommand.builder("literature_search")
                .arguments(Map.of("query", "RAG"))
                .build());

        assertTrue(result.isSuccess());
        assertEquals(3, result.getResult().get("hitCount"));
        assertEquals(List.of("literature_search"), registry.toolNames());
        assertEquals(1, registry.listEnabledDefinitions().size());
    }

    @Test
    void shouldRejectMissingRequiredArgument() {
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.register(definition("literature_search", true), command -> Map.of());

        AppException exception = assertThrows(AppException.class,
                () -> registry.call(AgentToolCallCommand.builder("literature_search").build()));

        assertEquals("TOOL_0004", exception.getCode());
    }

    @Test
    void shouldHideDisabledToolFromEnabledListAndRejectCall() {
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.register(definition("code_interpreter", false), command -> Map.of("ok", true));

        assertTrue(registry.listEnabledDefinitions().isEmpty());
        assertFalse(registry.listDefinitions().isEmpty());
        AppException exception = assertThrows(AppException.class,
                () -> registry.call(AgentToolCallCommand.builder("code_interpreter").build()));
        assertEquals("TOOL_0003", exception.getCode());
    }

    @Test
    void shouldRegisterStructuredToolOutputAndExposeArtifactIds() {
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.registerStructured(definition("report_tool", true), command ->
                AgentToolStructuredOutput.builder(AgentToolOutputNames.REPORT_TOOL)
                        .summary("报告已生成")
                        .addFileRef(AgentToolFileRef.builder()
                                .artifactId("A3001")
                                .fileName("report.md")
                                .build())
                        .build());

        AgentToolCallResult result = registry.call(AgentToolCallCommand.builder("report_tool")
                .arguments(Map.of("query", "RAG"))
                .build());

        assertTrue(result.isSuccess());
        assertEquals("报告已生成", result.getResult().get("summary"));
        assertEquals(List.of("A3001"), result.getArtifactIds());
    }

    @Test
    void shouldSummarizeRuntimeCoverageByStatusCategoryAndSource() {
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.register(definition("data_analysis", true), command -> Map.of("ok", true));
        registry.register(definition("code_interpreter", false), command -> Map.of("ok", true));

        AgentToolRuntimeSummary summary = registry.runtimeSummary(List.of(
                "data_analysis", "code_interpreter", "report_tool", "data_analysis", ""));

        assertEquals(2, summary.totalCount());
        assertEquals(1, summary.enabledCount());
        assertEquals(1, summary.disabledCount());
        assertEquals(List.of("data_analysis", "code_interpreter"), summary.registeredToolNames());
        assertEquals(List.of("data_analysis"), summary.enabledToolNames());
        assertEquals(List.of("code_interpreter"), summary.disabledToolNames());
        assertEquals(List.of("code_interpreter", "report_tool"), summary.missingExpectedToolNames());
        assertEquals(2, summary.categoryCounts().get("test"));
        assertEquals(2, summary.sourceCounts().get("unit"));
        assertFalse(summary.coversAllExpectedTools());
    }

    private AgentToolDefinition definition(String name, boolean enabled) {
        return AgentToolDefinition.builder(name)
                .description("test tool")
                .category("test")
                .source("unit")
                .inputSchema(Map.of("type", "object", "properties", Map.of("query", Map.of("type", "string"))))
                .requiredArguments(List.of("query"))
                .enabled(enabled)
                .build();
    }
}















