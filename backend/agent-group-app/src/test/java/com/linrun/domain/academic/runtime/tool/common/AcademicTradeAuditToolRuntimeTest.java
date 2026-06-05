package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicReportPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTradeAuditPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicTradeAuditToolRuntimeTest {

    @Test
    void shouldUseCommandUserIdAndReturnStructuredFindings() {
        AcademicTradeAuditToolRuntime runtime = new AcademicTradeAuditToolRuntime(request ->
                new AcademicTradeAuditPort.AcademicTradeAuditResult(
                        true,
                        "trade facts checked, highestSeverity=INFO, riskCount=0",
                        Map.of("orderId", request.orderId(), "userId", request.userId()),
                        List.of(Map.of(
                                "severity", "INFO",
                                "code", "PAID_WAITING_GROUP_SETTLEMENT",
                                "message", "Payment succeeded, but group settlement has not completed.")),
                        Map.of("highestSeverity", "INFO"),
                        ""));

        AcademicToolStructuredOutput output = runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.TRADE_AUDIT)
                .userId("U1001")
                .arguments(Map.of("orderId", "O1001"))
                .build());

        assertEquals(AcademicToolOutputNames.TRADE_AUDIT, output.getToolName());
        assertEquals("O1001", output.getTitle());
        assertTrue(output.getContent().contains("# Trade Audit Report"));
        assertTrue(output.getContent().contains("PAID_WAITING_GROUP_SETTLEMENT"));
        Map<String, Object> metadata = output.getMetadata();
        assertEquals(1, metadata.get("findingCount"));
        assertEquals("markdown", metadata.get("reportFormat"));
        assertTrue(String.valueOf(metadata.get("snapshot")).contains("U1001"));
        assertTrue(String.valueOf(metadata.get("findings")).contains("PAID_WAITING_GROUP_SETTLEMENT"));
    }

    @Test
    void shouldMaterializeAuditReportWhenReportPortIsConfigured() {
        AcademicTradeAuditToolRuntime runtime = new AcademicTradeAuditToolRuntime(
                request -> new AcademicTradeAuditPort.AcademicTradeAuditResult(
                        true,
                        "trade facts checked",
                        Map.of("orderId", request.orderId(), "userId", request.userId()),
                        List.of(Map.of("severity", "INFO", "code", "NO_BLOCKING_RISK")),
                        Map.of("highestSeverity", "INFO"),
                        ""),
                request -> new AcademicReportPort.AcademicReportResult(
                        true,
                        "report saved",
                        "report saved",
                        List.of(AcademicToolFileRef.builder()
                                .artifactId("A1001")
                                .fileName(request.fileName())
                                .downloadUrl("/files/" + request.fileName())
                                .contentType("text/markdown")
                                .build()),
                        Map.of("provider", "mock"),
                        ""));

        AcademicToolStructuredOutput output = runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.TRADE_AUDIT)
                .requestId("REQ1001")
                .userId("U1001")
                .arguments(Map.of("orderId", "O1001"))
                .build());

        assertEquals(1, output.getFileRefs().size());
        assertEquals("trade-audit-O1001.md", output.getFileRefs().getFirst().getFileName());
        assertEquals(Boolean.TRUE, output.getMetadata().get("reportMaterialized"));
    }

    @Test
    void shouldFailWhenPortIsMissing() {
        AcademicTradeAuditToolRuntime runtime = new AcademicTradeAuditToolRuntime(null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.TRADE_AUDIT).build()));

        assertEquals("TRADE_AUDIT_0001", exception.getCode());
    }
}
