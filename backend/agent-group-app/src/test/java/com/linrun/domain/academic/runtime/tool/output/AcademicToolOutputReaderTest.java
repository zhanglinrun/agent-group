package com.linrun.domain.academic.runtime.tool.output;

import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.model.AcademicToolInvocation;
import com.linrun.domain.academic.model.AcademicArtifact;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicToolOutputReaderTest {

    private final AcademicToolOutputReader reader = new AcademicToolOutputReader();

    @Test
    void shouldReadStructuredOutputAndAttachArtifacts() {
        AcademicToolInvocation invocation = invocation("""
                {
                  "toolName": "image_generation",
                  "summary": "生成两张海报",
                  "fileRefs": [
                    {
                      "artifactId": "A1001",
                      "fileName": "poster.png",
                      "downloadUrl": "/artifacts/A1001",
                      "contentType": "image/png",
                      "fileSize": 1024
                    }
                  ]
                }
                """);
        AcademicArtifact artifact = artifact("A1001", "TOOL1001", "海报", "/tmp/poster.png");

        AcademicToolOutputView view = reader.read(invocation, List.of(artifact));

        assertEquals("image_generation", view.getToolName());
        assertEquals("生成两张海报", view.getStructuredOutput().get("summary"));
        assertEquals(1, view.getArtifactCount());
        assertEquals("A1001", view.getArtifactRefs().getFirst().getArtifactId());
        assertEquals("poster.png", view.getFileRefs().getFirst().getFileName());
    }

    @Test
    void shouldReadFileInfoAndNestedPrimaryFileRefs() {
        AcademicToolInvocation invocation = invocation("""
                {
                  "toolName": "code_interpreter",
                  "fileInfo": [
                    {
                      "displayName": "code-output.md",
                      "domainUrl": "/tool/files/code-output.md",
                      "mimeType": "text/markdown",
                      "resourceKey": "code-output-resource"
                    }
                  ],
                  "result": {
                    "primaryFileName": "summary.csv",
                    "ossUrl": "/files/summary.csv",
                    "mimeType": "text/csv"
                  }
                }
                """);

        AcademicToolOutputView view = reader.read(invocation, List.of());

        assertEquals(2, view.getFileRefs().size());
        assertEquals("code-output.md", view.getFileRefs().get(0).getFileName());
        assertEquals("code-output-resource", view.getFileRefs().get(0).getArtifactId());
        assertEquals("/tool/files/code-output.md", view.getFileRefs().get(0).getDownloadUrl());
        assertEquals("summary.csv", view.getFileRefs().get(1).getFileName());
        assertEquals("/files/summary.csv", view.getFileRefs().get(1).getDownloadUrl());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExposeTradeAuditFieldsAndStableReportRefs() {
        AcademicToolInvocation invocation = invocation("""
                {
                  "toolName": "trade_audit",
                  "title": "O1001",
                  "summary": "trade facts checked",
                  "metadata": {
                    "snapshot": {
                      "orderId": "O1001",
                      "orderStatus": "PAY_SUCCESS",
                      "auditConclusion": {
                        "code": "WAIT_GROUP_SETTLEMENT",
                        "quotaGrantAllowed": false,
                        "quotaRollbackRequired": false,
                        "suggestedAction": "wait group settlement"
                      }
                    },
                    "findings": [
                      {
                        "severity": "WARN",
                        "code": "WAITING_GROUP_SETTLEMENT",
                        "message": "Payment succeeded, but group settlement has not completed."
                      }
                    ],
                    "highestSeverity": "WARN",
                    "reportMaterialized": true
                  },
                  "fileRefs": [
                    {
                      "artifactId": "A2001",
                      "fileName": "trade-audit-O1001.md",
                      "contentType": "text/markdown"
                    }
                  ]
                }
                """);
        invocation.setToolName(AcademicToolOutputNames.TRADE_AUDIT);
        AcademicArtifact artifact = artifact("A2001", "TOOL1001", "Trade Audit", "trade-audit-O1001.md");
        artifact.setArtifactType("MD");

        AcademicToolOutputView view = reader.read(invocation, List.of(artifact));
        Map<String, Object> output = view.getStructuredOutput();
        List<Map<String, Object>> findings = (List<Map<String, Object>>) output.get("findings");
        Map<String, Object> snapshot = (Map<String, Object>) output.get("snapshot");
        Map<String, Object> conclusion = (Map<String, Object>) output.get("auditConclusion");

        assertEquals("trade", output.get("auditKind"));
        assertEquals("WARN", output.get("highestSeverity"));
        assertEquals(1, output.get("findingCount"));
        assertEquals(Boolean.TRUE, output.get("reportMaterialized"));
        assertEquals("O1001", snapshot.get("orderId"));
        assertEquals("WAIT_GROUP_SETTLEMENT", conclusion.get("code"));
        assertEquals("WAIT_GROUP_SETTLEMENT", output.get("conclusionCode"));
        assertEquals(false, output.get("quotaGrantAllowed"));
        assertEquals(false, output.get("quotaRollbackRequired"));
        assertEquals("wait group settlement", output.get("suggestedAction"));
        assertEquals("WAITING_GROUP_SETTLEMENT", findings.getFirst().get("code"));
        assertEquals("/artifacts/A2001", view.getFileRefs().getFirst().getDownloadUrl());
    }

    @Test
    void shouldNotTreatPlainTitleAsFileRef() {
        AcademicToolInvocation invocation = invocation("""
                {
                  "toolName": "planning_tool",
                  "title": "Trade plan",
                  "summary": "Plain text result",
                  "result": {
                    "title": "Nested title",
                    "summary": "Nested plain text"
                  }
                }
                """);

        AcademicToolOutputView view = reader.read(invocation, List.of());

        assertTrue(view.getFileRefs().isEmpty());
        assertEquals(0, view.getArtifactCount());
        assertFalse(view.getStructuredOutput().containsKey("fileRefs"));
    }

    @Test
    void shouldBuildFallbackOutputWhenResultJsonIsBlank() {
        AcademicToolInvocation invocation = invocation("");
        invocation.setResultSummary("报告已生成");

        AcademicToolOutputView view = reader.read(invocation, List.of());

        assertEquals("报告已生成", view.getStructuredOutput().get("summary"));
        assertTrue((Boolean) view.getStructuredOutput().get("success"));
    }

    private AcademicToolInvocation invocation(String resultJson) {
        AcademicToolInvocation invocation = new AcademicToolInvocation();
        invocation.setInvocationId("TOOL1001");
        invocation.setRunId("RUN1001");
        invocation.setRequestId("REQ1001");
        invocation.setSessionId("S1001");
        invocation.setToolCallId("CALL1001");
        invocation.setToolName(AcademicToolOutputNames.IMAGE_GENERATION);
        invocation.setResultJson(resultJson);
        invocation.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        invocation.setFinishedAt(LocalDateTime.now());
        return invocation;
    }

    private AcademicArtifact artifact(String artifactId,
                                      String toolInvocationId,
                                      String title,
                                      String content) {
        AcademicArtifact artifact = new AcademicArtifact();
        artifact.setArtifactId(artifactId);
        artifact.setToolInvocationId(toolInvocationId);
        artifact.setTitle(title);
        artifact.setContent(content);
        artifact.setDownloadUrl("/artifacts/" + artifactId);
        artifact.setArtifactType("image/png");
        return artifact;
    }
}
