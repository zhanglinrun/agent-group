package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.port.AcademicTableRagPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicTableRagToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldRecallTableSchemasThroughPort() {
        AcademicTableRagPort port = request -> new AcademicTableRagPort.AcademicTableRagResult(
                true,
                request.requestId(),
                List.of(new AcademicTableRagPort.AcademicTableSchemaMatch(
                        "experiment_result",
                        0.92D,
                        List.of(Map.of("column", "metric_name", "type", "varchar")))),
                Map.of("provider", "mock-table-rag"),
                "");
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.registerStructured(AcademicTableRagToolRuntime.definition(), new AcademicTableRagToolRuntime(port)::call);

        AcademicToolCallResult result = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.TABLE_RAG)
                .arguments(Map.of("query", "experiment metrics"))
                .build());

        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        assertTrue(result.isSuccess());
        assertEquals("mock-table-rag", metadata.get("provider"));
        assertEquals(1, metadata.get("matchCount"));
    }

    @Test
    void shouldRejectWhenPortMissing() {
        AcademicTableRagToolRuntime runtime = new AcademicTableRagToolRuntime(null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.TABLE_RAG)
                        .arguments(Map.of("query", "experiments"))
                        .build()));

        assertEquals("TABLE_RAG_0001", exception.getCode());
    }
}















