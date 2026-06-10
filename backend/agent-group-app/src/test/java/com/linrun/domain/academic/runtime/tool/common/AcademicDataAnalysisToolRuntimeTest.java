package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicDataAnalysisToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldAnalyzeRowsAndNumericStats() {
        AcademicDataAnalysisToolRuntime dataAnalysisTool = new AcademicDataAnalysisToolRuntime();
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.registerStructured(AcademicDataAnalysisToolRuntime.definition(), dataAnalysisTool::call);

        AcademicToolCallResult result = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.DATA_ANALYSIS)
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















