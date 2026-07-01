package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolCallResult;
import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.port.AgentTableRagPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTableRagToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldRecallTableSchemasThroughPort() {
        AgentTableRagPort port = request -> new AgentTableRagPort.AgentTableRagResult(
                true,
                request.requestId(),
                List.of(new AgentTableRagPort.AgentTableSchemaMatch(
                        "experiment_result",
                        0.92D,
                        List.of(Map.of("column", "metric_name", "type", "varchar")))),
                Map.of("provider", "mock-table-rag"),
                "");
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.registerStructured(AgentTableRagToolRuntime.definition(), new AgentTableRagToolRuntime(port)::call);

        AgentToolCallResult result = registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.TABLE_RAG)
                .arguments(Map.of("query", "experiment metrics"))
                .build());

        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        assertTrue(result.isSuccess());
        assertEquals("mock-table-rag", metadata.get("provider"));
        assertEquals(1, metadata.get("matchCount"));
    }

    @Test
    void shouldRejectWhenPortMissing() {
        AgentTableRagToolRuntime runtime = new AgentTableRagToolRuntime(null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.TABLE_RAG)
                        .arguments(Map.of("query", "experiments"))
                        .build()));

        assertEquals("TABLE_RAG_0001", exception.getCode());
    }
}















