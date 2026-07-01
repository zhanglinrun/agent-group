package com.linrun.domain.agent.runtime.tool;

import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolCollectionTest {

    @Test
    void shouldBuildSceneCollectionByCategoryAndCallOnlyEnabledTool() {
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.register(definition("literature_search", "research", "mcp"), command -> Map.of("hitCount", 3));
        registry.register(definition("data_analysis", "analysis", "local"), command -> Map.of("rows", 3));

        AgentToolCollection collection = new AgentToolCollectionFactory(registry)
                .buildByCategories("research_agent", List.of("research"));

        assertEquals(List.of("literature_search"), collection.toolNames());
        assertEquals(3, collection.call(AgentToolCallCommand.builder("literature_search").build())
                .getResult().get("hitCount"));

        AppException exception = assertThrows(AppException.class,
                () -> collection.call(AgentToolCallCommand.builder("data_analysis").build()));
        assertEquals("TOOL_0101", exception.getCode());
    }

    @Test
    void shouldCarryTaskStateWhenSelectingChildCollection() {
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.register(definition("report_tool", "report", "local"), command -> Map.of("ok", true));
        AgentToolCollection collection = new AgentToolCollectionFactory(registry).buildAll("deep_research");
        collection.updateCurrentTask("整理长文档报告");
        collection.updateToolRoles(Map.of("report_tool", "报告生成专员"));

        AgentToolCollection child = collection.select("parallel_task", List.of("report_tool"));

        assertTrue(child.contains("report_tool"));
        assertEquals("整理长文档报告", child.getCurrentTask());
        assertEquals("报告生成专员", child.getToolRole("report_tool"));
    }

    private AgentToolDefinition definition(String name, String category, String source) {
        return AgentToolDefinition.builder(name)
                .description("test tool")
                .category(category)
                .source(source)
                .inputSchema(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                .requiredArguments(List.of())
                .enabled(true)
                .build();
    }
}















