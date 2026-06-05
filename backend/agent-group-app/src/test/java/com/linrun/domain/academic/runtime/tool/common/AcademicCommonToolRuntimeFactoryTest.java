package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.port.AcademicCodeInterpreterPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicDeepSearchPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicFileToolPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicImageGenerationPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicMultimodalAnalysisPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicNl2SqlPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicScriptRunnerPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTableRagPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTradeAuditPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicWebFetchPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicCommonToolRuntimeFactoryTest {

    @Test
    void shouldBuildLocalSafeToolRegistryByDefault() {
        AcademicToolRuntimeRegistry registry = AcademicCommonToolRuntimeFactory.builder().build().buildRegistry();

        assertEquals(List.of(
                AcademicToolOutputNames.WEB_FETCH,
                AcademicToolOutputNames.DATA_ANALYSIS,
                AcademicToolOutputNames.REPORT_TOOL,
                AcademicToolOutputNames.PLANNING), registry.toolNames());
    }

    @Test
    void shouldBuildFullRichToolRegistryWhenPortsAreConfigured() {
        AcademicToolRuntimeRegistry registry = AcademicCommonToolRuntimeFactory.builder()
                .codeInterpreterPort(codePort())
                .webFetchPort(webFetchPort())
                .imageGenerationPort(imagePort())
                .multimodalAnalysisPort(multimodalPort())
                .deepSearchPort(deepSearchPort())
                .fileToolPort(fileToolPort())
                .scriptRunnerPort(scriptPort())
                .tableRagPort(tableRagPort())
                .nl2SqlPort(nl2SqlPort())
                .tradeAuditPort(tradeAuditPort())
                .build()
                .buildRegistry();

        assertTrue(registry.toolNames().containsAll(AcademicToolOutputNames.RICH_TOOL_NAMES));
        assertEquals("script executed", registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.SCRIPT_RUNNER)
                .arguments(Map.of("skillName", "demo", "scriptName", "run"))
                .build()).getResult().get("summary"));
        assertEquals("remote page", registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.WEB_FETCH)
                .arguments(Map.of("url", "https://example.com/doc"))
                .build()).getResult().get("content"));
    }

    private AcademicWebFetchPort webFetchPort() {
        return request -> new AcademicWebFetchPort.AcademicWebFetchResult(
                true,
                "Example Doc",
                request.url(),
                "remote page",
                "remote page",
                List.of(),
                Map.of("provider", "mock"),
                "");
    }

    private AcademicCodeInterpreterPort codePort() {
        return request -> new AcademicCodeInterpreterPort.AcademicCodeExecutionResult(
                true, 0, "ok", "", "ok", request.code(), "done", List.of());
    }

    private AcademicImageGenerationPort imagePort() {
        return request -> new AcademicImageGenerationPort.AcademicImageGenerationResult(
                true, "mock", "image generated", false, List.of(), "");
    }

    private AcademicMultimodalAnalysisPort multimodalPort() {
        return request -> new AcademicMultimodalAnalysisPort.AcademicMultimodalAnalysisResult(
                true, "analysis done", "analysis done", Map.of(), List.of(), "");
    }

    private AcademicDeepSearchPort deepSearchPort() {
        return request -> new AcademicDeepSearchPort.AcademicDeepSearchResult(
                true, request.query(), "answer", "answer", List.of(), List.of(), List.of(), Map.of(), "");
    }

    private AcademicTableRagPort tableRagPort() {
        return request -> new AcademicTableRagPort.AcademicTableRagResult(
                true,
                request.requestId(),
                List.of(new AcademicTableRagPort.AcademicTableSchemaMatch("trade_order", 0.9D, List.of())),
                Map.of(),
                "");
    }

    private AcademicNl2SqlPort nl2SqlPort() {
        return request -> new AcademicNl2SqlPort.AcademicNl2SqlResult(
                true,
                request.requestId(),
                request.query(),
                "think",
                "done",
                List.of(new AcademicNl2SqlPort.AcademicSqlCandidate(request.query(), "select 1")),
                Map.of(),
                "");
    }

    private AcademicTradeAuditPort tradeAuditPort() {
        return request -> new AcademicTradeAuditPort.AcademicTradeAuditResult(
                true,
                "trade facts checked",
                Map.of("orderId", request.orderId()),
                List.of(Map.of("severity", "INFO", "code", "NO_BLOCKING_RISK")),
                Map.of(),
                "");
    }

    private AcademicScriptRunnerPort scriptPort() {
        return request -> new AcademicScriptRunnerPort.AcademicScriptRunResult(
                true, 0, "ok", "", "script executed", List.of(), Map.of(), "");
    }

    private AcademicFileToolPort fileToolPort() {
        return new AcademicFileToolPort() {
            @Override
            public AcademicFileToolResult upload(AcademicFileUploadRequest request) {
                return new AcademicFileToolResult(
                        true, "upload", request.fileName(), "", "uploaded", List.of(), Map.of(), "");
            }

            @Override
            public AcademicFileToolResult get(AcademicFileGetRequest request) {
                return new AcademicFileToolResult(
                        true, "get", request.fileName(), "content", "loaded", List.of(), Map.of(), "");
            }
        };
    }
}
