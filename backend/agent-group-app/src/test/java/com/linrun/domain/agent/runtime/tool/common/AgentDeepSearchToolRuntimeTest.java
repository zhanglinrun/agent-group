package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolCallResult;
import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.port.AgentDeepSearchPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDeepSearchToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldSearchThroughPortAndExposeDocuments() {
        AgentDeepSearchPort port = request -> new AgentDeepSearchPort.AgentDeepSearchResult(
                true,
                request.query(),
                "Group orders should grant quota after settlement.",
                "Quota is granted after group settlement.",
                List.of("group payment status", "quota grant timing"),
                List.of(new AgentDeepSearchPort.AgentDeepSearchDocument(
                        "Settlement rule",
                        "https://example.test/rule",
                        "PAY_SUCCESS is not enough for group-buy quota.",
                        "web")),
                List.of(AgentToolFileRef.builder()
                        .artifactId("A-DEEP-1")
                        .fileName("search.md")
                        .build()),
                Map.of("provider", "mock-search"),
                "");
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.registerStructured(AgentDeepSearchToolRuntime.definition(),
                new AgentDeepSearchToolRuntime(port)::call);

        AgentToolCallResult result = registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.DEEP_SEARCH)
                .arguments(Map.of("query", "group-buy quota settlement", "maxResults", 3))
                .build());

        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        assertTrue(result.isSuccess());
        assertEquals("mock-search", metadata.get("provider"));
        assertEquals(1, metadata.get("documentCount"));
        assertEquals(List.of("A-DEEP-1"), result.getArtifactIds());
    }

    @Test
    void shouldSurfaceSearchFailure() {
        AgentDeepSearchPort port = request -> new AgentDeepSearchPort.AgentDeepSearchResult(
                false, request.query(), "", "", List.of(), List.of(), List.of(), Map.of(), "search unavailable");
        AgentDeepSearchToolRuntime runtime = new AgentDeepSearchToolRuntime(port);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.DEEP_SEARCH)
                        .arguments(Map.of("query", "quota"))
                        .build()));

        assertEquals("DEEP_SEARCH_0003", exception.getCode());
    }
}















