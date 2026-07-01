package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.port.AgentCodeInterpreterPort;
import com.linrun.domain.agent.runtime.tool.port.AgentDeepSearchPort;
import com.linrun.domain.agent.runtime.tool.port.AgentFileToolPort;
import com.linrun.domain.agent.runtime.tool.port.AgentImageGenerationPort;
import com.linrun.domain.agent.runtime.tool.port.AgentMultimodalAnalysisPort;
import com.linrun.domain.agent.runtime.tool.port.AgentNl2SqlPort;
import com.linrun.domain.agent.runtime.tool.port.AgentScriptRunnerPort;
import com.linrun.domain.agent.runtime.tool.port.AgentTableRagPort;
import com.linrun.domain.agent.runtime.tool.port.AgentWebFetchPort;
import com.linrun.domain.trade.service.TradeConsistencyCheckService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCommonToolRuntimeFactoryTest {

    @Test
    void shouldBuildLocalSafeToolRegistryByDefault() {
        AgentToolRuntimeRegistry registry = AgentCommonToolRuntimeFactory.builder().build().buildRegistry();

        assertEquals(List.of(
                AgentToolOutputNames.WEB_FETCH,
                AgentToolOutputNames.DATA_ANALYSIS,
                AgentToolOutputNames.REPORT_TOOL,
                AgentToolOutputNames.PLANNING), registry.toolNames());
    }

    @Test
    void shouldBuildFullRichToolRegistryWhenPortsAreConfigured() {
        AgentToolRuntimeRegistry registry = AgentCommonToolRuntimeFactory.builder()
                .codeInterpreterPort(codePort())
                .webFetchPort(webFetchPort())
                .imageGenerationPort(imagePort())
                .multimodalAnalysisPort(multimodalPort())
                .deepSearchPort(deepSearchPort())
                .fileToolPort(fileToolPort())
                .scriptRunnerPort(scriptPort())
                .tableRagPort(tableRagPort())
                .nl2SqlPort(nl2SqlPort())
                .tradeConsistencyCheckService(new TradeConsistencyCheckService(null, null))
                .build()
                .buildRegistry();

        assertTrue(registry.toolNames().containsAll(AgentToolOutputNames.RICH_TOOL_NAMES));
        assertEquals("script executed", registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.SCRIPT_RUNNER)
                .arguments(Map.of("skillName", "demo", "scriptName", "run"))
                .build()).getResult().get("summary"));
        assertEquals("remote page", registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.WEB_FETCH)
                .arguments(Map.of("url", "https://example.com/doc"))
                .build()).getResult().get("content"));
    }

    private AgentWebFetchPort webFetchPort() {
        return request -> new AgentWebFetchPort.AgentWebFetchResult(
                true,
                "Example Doc",
                request.url(),
                "remote page",
                "remote page",
                List.of(),
                Map.of("provider", "mock"),
                "");
    }

    private AgentCodeInterpreterPort codePort() {
        return request -> new AgentCodeInterpreterPort.AgentCodeExecutionResult(
                true, 0, "ok", "", "ok", request.code(), "done", List.of());
    }

    private AgentImageGenerationPort imagePort() {
        return request -> new AgentImageGenerationPort.AgentImageGenerationResult(
                true, "mock", "image generated", false, List.of(), "");
    }

    private AgentMultimodalAnalysisPort multimodalPort() {
        return request -> new AgentMultimodalAnalysisPort.AgentMultimodalAnalysisResult(
                true, "analysis done", "analysis done", Map.of(), List.of(), "");
    }

    private AgentDeepSearchPort deepSearchPort() {
        return request -> new AgentDeepSearchPort.AgentDeepSearchResult(
                true, request.query(), "answer", "answer", List.of(), List.of(), List.of(), Map.of(), "");
    }

    private AgentTableRagPort tableRagPort() {
        return request -> new AgentTableRagPort.AgentTableRagResult(
                true,
                request.requestId(),
                List.of(new AgentTableRagPort.AgentTableSchemaMatch("experiment_result", 0.9D, List.of())),
                Map.of(),
                "");
    }

    private AgentNl2SqlPort nl2SqlPort() {
        return request -> new AgentNl2SqlPort.AgentNl2SqlResult(
                true,
                request.requestId(),
                request.query(),
                "think",
                "done",
                List.of(new AgentNl2SqlPort.AgentSqlCandidate(request.query(), "select 1")),
                Map.of(),
                "");
    }

    private AgentScriptRunnerPort scriptPort() {
        return request -> new AgentScriptRunnerPort.AgentScriptRunResult(
                true, 0, "ok", "", "script executed", List.of(), Map.of(), "");
    }

    private AgentFileToolPort fileToolPort() {
        return new AgentFileToolPort() {
            @Override
            public AgentFileToolResult upload(AgentFileUploadRequest request) {
                return new AgentFileToolResult(
                        true, "upload", request.fileName(), "", "uploaded", List.of(), Map.of(), "");
            }

            @Override
            public AgentFileToolResult get(AgentFileGetRequest request) {
                return new AgentFileToolResult(
                        true, "get", request.fileName(), "content", "loaded", List.of(), Map.of(), "");
            }
        };
    }
}















