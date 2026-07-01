package com.linrun.domain.agent.runtime.tool.output;

import com.linrun.domain.agent.ledger.model.AgentRun;
import com.linrun.domain.agent.ledger.model.AgentToolInvocation;
import com.linrun.domain.agent.model.AgentArtifact;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolOutputReaderTest {

    private final AgentToolOutputReader reader = new AgentToolOutputReader();

    @Test
    void shouldReadStructuredOutputAndAttachArtifacts() {
        AgentToolInvocation invocation = invocation("""
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
        AgentArtifact artifact = artifact("A1001", "TOOL1001", "海报", "/tmp/poster.png");

        AgentToolOutputView view = reader.read(invocation, List.of(artifact));

        assertEquals("image_generation", view.getToolName());
        assertEquals("生成两张海报", view.getStructuredOutput().get("summary"));
        assertEquals(1, view.getArtifactCount());
        assertEquals("A1001", view.getArtifactRefs().getFirst().getArtifactId());
        assertEquals("poster.png", view.getFileRefs().getFirst().getFileName());
    }

    @Test
    void shouldReadFileInfoAndNestedPrimaryFileRefs() {
        AgentToolInvocation invocation = invocation("""
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

        AgentToolOutputView view = reader.read(invocation, List.of());

        assertEquals(2, view.getFileRefs().size());
        assertEquals("code-output.md", view.getFileRefs().get(0).getFileName());
        assertEquals("code-output-resource", view.getFileRefs().get(0).getArtifactId());
        assertEquals("/tool/files/code-output.md", view.getFileRefs().get(0).getDownloadUrl());
        assertEquals("summary.csv", view.getFileRefs().get(1).getFileName());
        assertEquals("/files/summary.csv", view.getFileRefs().get(1).getDownloadUrl());
    }

    @Test
    void shouldNotTreatPlainTitleAsFileRef() {
        AgentToolInvocation invocation = invocation("""
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

        AgentToolOutputView view = reader.read(invocation, List.of());

        assertTrue(view.getFileRefs().isEmpty());
        assertEquals(0, view.getArtifactCount());
        assertFalse(view.getStructuredOutput().containsKey("fileRefs"));
    }

    @Test
    void shouldBuildFallbackOutputWhenResultJsonIsBlank() {
        AgentToolInvocation invocation = invocation("");
        invocation.setResultSummary("报告已生成");

        AgentToolOutputView view = reader.read(invocation, List.of());

        assertEquals("报告已生成", view.getStructuredOutput().get("summary"));
        assertTrue((Boolean) view.getStructuredOutput().get("success"));
    }

    private AgentToolInvocation invocation(String resultJson) {
        AgentToolInvocation invocation = new AgentToolInvocation();
        invocation.setInvocationId("TOOL1001");
        invocation.setRunId("RUN1001");
        invocation.setRequestId("REQ1001");
        invocation.setSessionId("S1001");
        invocation.setToolCallId("CALL1001");
        invocation.setToolName(AgentToolOutputNames.IMAGE_GENERATION);
        invocation.setResultJson(resultJson);
        invocation.setStatus(AgentRun.STATUS_SUCCESS);
        invocation.setFinishedAt(LocalDateTime.now());
        return invocation;
    }

    private AgentArtifact artifact(String artifactId,
                                      String toolInvocationId,
                                      String title,
                                      String content) {
        AgentArtifact artifact = new AgentArtifact();
        artifact.setArtifactId(artifactId);
        artifact.setToolInvocationId(toolInvocationId);
        artifact.setTitle(title);
        artifact.setContent(content);
        artifact.setDownloadUrl("/artifacts/" + artifactId);
        artifact.setArtifactType("image/png");
        return artifact;
    }
}















