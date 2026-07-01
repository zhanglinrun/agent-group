package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolCallResult;
import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.port.AgentNl2SqlPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNl2SqlToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldConvertQuestionToSqlThroughPort() {
        AgentNl2SqlPort port = request -> new AgentNl2SqlPort.AgentNl2SqlResult(
                true,
                request.requestId(),
                request.query(),
                "filter experiment metrics",
                "done",
                List.of(new AgentNl2SqlPort.AgentSqlCandidate(
                        request.query(),
                        "select avg(metric_value) from experiment_result where metric_name = 'accuracy'")),
                Map.of("provider", "mock-nl2sql"),
                "");
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.registerStructured(AgentNl2SqlToolRuntime.definition(), new AgentNl2SqlToolRuntime(port)::call);

        AgentToolCallResult result = registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.NL2SQL)
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
        AgentNl2SqlPort port = request -> new AgentNl2SqlPort.AgentNl2SqlResult(
                false, request.requestId(), request.query(), "", "failed", List.of(), Map.of(), "schema missing");
        AgentNl2SqlToolRuntime runtime = new AgentNl2SqlToolRuntime(port);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.NL2SQL)
                        .arguments(Map.of("query", "average metrics"))
                        .build()));

        assertEquals("NL2SQL_0003", exception.getCode());
    }
}















