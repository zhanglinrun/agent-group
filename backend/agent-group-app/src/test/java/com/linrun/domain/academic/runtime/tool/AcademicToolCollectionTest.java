package com.linrun.domain.academic.runtime.tool;

import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicToolCollectionTest {

    @Test
    void shouldBuildSceneCollectionByCategoryAndCallOnlyEnabledTool() {
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.register(definition("order_status", "trade", "mcp"), command -> Map.of("status", "PAY_SUCCESS"));
        registry.register(definition("data_analysis", "analysis", "local"), command -> Map.of("rows", 3));

        AcademicToolCollection collection = new AcademicToolCollectionFactory(registry)
                .buildByCategories("trade_agent", List.of("trade"));

        assertEquals(List.of("order_status"), collection.toolNames());
        assertEquals("PAY_SUCCESS", collection.call(AcademicToolCallCommand.builder("order_status").build())
                .getResult().get("status"));

        AppException exception = assertThrows(AppException.class,
                () -> collection.call(AcademicToolCallCommand.builder("data_analysis").build()));
        assertEquals("TOOL_0101", exception.getCode());
    }

    @Test
    void shouldCarryTaskStateWhenSelectingChildCollection() {
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.register(definition("report_tool", "report", "local"), command -> Map.of("ok", true));
        AcademicToolCollection collection = new AcademicToolCollectionFactory(registry).buildAll("deep_research");
        collection.updateCurrentTask("整理交易链路报告");
        collection.updateToolRoles(Map.of("report_tool", "报告生成专员"));

        AcademicToolCollection child = collection.select("parallel_task", List.of("report_tool"));

        assertTrue(child.contains("report_tool"));
        assertEquals("整理交易链路报告", child.getCurrentTask());
        assertEquals("报告生成专员", child.getToolRole("report_tool"));
    }

    private AcademicToolDefinition definition(String name, String category, String source) {
        return AcademicToolDefinition.builder(name)
                .description("test tool")
                .category(category)
                .source(source)
                .inputSchema(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                .requiredArguments(List.of())
                .enabled(true)
                .build();
    }
}
