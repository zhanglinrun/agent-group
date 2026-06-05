package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.port.AcademicDeepSearchPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicDeepSearchToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldSearchThroughPortAndExposeDocuments() {
        AcademicDeepSearchPort port = request -> new AcademicDeepSearchPort.AcademicDeepSearchResult(
                true,
                request.query(),
                "Group orders should grant quota after settlement.",
                "Quota is granted after group settlement.",
                List.of("group payment status", "quota grant timing"),
                List.of(new AcademicDeepSearchPort.AcademicDeepSearchDocument(
                        "Settlement rule",
                        "https://example.test/rule",
                        "PAY_SUCCESS is not enough for group-buy quota.",
                        "web")),
                List.of(AcademicToolFileRef.builder()
                        .artifactId("A-DEEP-1")
                        .fileName("search.md")
                        .build()),
                Map.of("provider", "mock-search"),
                "");
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.registerStructured(AcademicDeepSearchToolRuntime.definition(),
                new AcademicDeepSearchToolRuntime(port)::call);

        AcademicToolCallResult result = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.DEEP_SEARCH)
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
        AcademicDeepSearchPort port = request -> new AcademicDeepSearchPort.AcademicDeepSearchResult(
                false, request.query(), "", "", List.of(), List.of(), List.of(), Map.of(), "search unavailable");
        AcademicDeepSearchToolRuntime runtime = new AcademicDeepSearchToolRuntime(port);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.DEEP_SEARCH)
                        .arguments(Map.of("query", "quota"))
                        .build()));

        assertEquals("DEEP_SEARCH_0003", exception.getCode());
    }
}
