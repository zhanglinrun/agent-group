package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.port.AcademicNl2SqlPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicNl2SqlToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldConvertQuestionToSqlThroughPort() {
        AcademicNl2SqlPort port = request -> new AcademicNl2SqlPort.AcademicNl2SqlResult(
                true,
                request.requestId(),
                request.query(),
                "filter experiment metrics",
                "done",
                List.of(new AcademicNl2SqlPort.AcademicSqlCandidate(
                        request.query(),
                        "select avg(metric_value) from experiment_result where metric_name = 'accuracy'")),
                Map.of("provider", "mock-nl2sql"),
                "");
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.registerStructured(AcademicNl2SqlToolRuntime.definition(), new AcademicNl2SqlToolRuntime(port)::call);

        AcademicToolCallResult result = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.NL2SQL)
                .arguments(Map.of("query", "average accuracy", "dbType", "mysql"))
                .build());

        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        assertTrue(result.isSuccess());
        assertEquals("mock-nl2sql", metadata.get("provider"));
        assertEquals(1, metadata.get("candidateCount"));
        assertTrue(String.valueOf(result.getResult().get("content")).contains("select avg(metric_value)"));
    }

    @Test
    void shouldSurfaceNl2SqlFailure() {
        AcademicNl2SqlPort port = request -> new AcademicNl2SqlPort.AcademicNl2SqlResult(
                false, request.requestId(), request.query(), "", "failed", List.of(), Map.of(), "schema missing");
        AcademicNl2SqlToolRuntime runtime = new AcademicNl2SqlToolRuntime(port);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.NL2SQL)
                        .arguments(Map.of("query", "average metrics"))
                        .build()));

        assertEquals("NL2SQL_0003", exception.getCode());
    }
}















