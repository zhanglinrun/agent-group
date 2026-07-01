package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolCallResult;
import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDataAnalysisToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldAnalyzeRowsAndNumericStats() {
        AgentDataAnalysisToolRuntime dataAnalysisTool = new AgentDataAnalysisToolRuntime();
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.registerStructured(AgentDataAnalysisToolRuntime.definition(), dataAnalysisTool::call);

        AgentToolCallResult result = registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.DATA_ANALYSIS)
                .arguments(Map.of(
                        "task", "分析套餐销量",
                        "rows", List.of(
                                Map.of("package", "A", "sales", 10, "refunds", 1),
                                Map.of("package", "B", "sales", 30, "refunds", 0),
                                Map.of("package", "C", "sales", 20))))
                .build());

        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        Map<String, Map<String, Object>> numericStats =
                (Map<String, Map<String, Object>>) metadata.get("numericStats");
        Map<String, Integer> missingValues = (Map<String, Integer>) metadata.get("missingValues");

        assertTrue(result.isSuccess());
        assertEquals(3, metadata.get("rowCount"));
        assertEquals("30", numericStats.get("sales").get("max"));
        assertEquals("20", numericStats.get("sales").get("avg"));
        assertEquals(1, missingValues.get("refunds"));
    }
}















