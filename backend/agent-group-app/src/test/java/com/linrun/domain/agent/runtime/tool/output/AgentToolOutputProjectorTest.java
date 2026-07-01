package com.linrun.domain.agent.runtime.tool.output;

import com.linrun.domain.agent.runtime.tool.AgentToolCallResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolOutputProjectorTest {

    @Test
    void shouldProjectStructuredOutputToResultMapAndArtifactIds() {
        AgentToolStructuredOutput output = AgentToolStructuredOutput.builder(AgentToolOutputNames.IMAGE_GENERATION)
                .title("配图生成")
                .summary("生成两张活动海报")
                .content("图片已生成")
                .putMetadata("prompt", "秋招项目展示")
                .addFileRef(AgentToolFileRef.builder()
                        .artifactId("A1001")
                        .fileName("poster.png")
                        .downloadUrl("/artifacts/A1001")
                        .contentType("image/png")
                        .fileSize(1024)
                        .build())
                .build();

        Map<String, Object> result = AgentToolOutputProjector.toResultMap(output);

        assertTrue(AgentToolOutputNames.isRichTool(AgentToolOutputNames.IMAGE_GENERATION));
        assertEquals("配图生成", result.get("title"));
        assertEquals(List.of("A1001"), AgentToolOutputProjector.extractArtifactIds(result));
        assertEquals("生成两张活动海报", AgentToolOutputProjector.summarize(output));
    }

    @Test
    void shouldExtractArtifactIdsFromCompatibilityFields() {
        Map<String, Object> result = Map.of(
                "artifactRefs", List.of(Map.of("artifactId", "A1001")),
                "fileInfo", List.of(Map.of("resourceKey", "FILE2001")),
                "fileList", List.of(Map.of("fileId", "FILE3001")),
                "result", Map.of("structuredOutput", Map.of(
                        "fileRefs", List.of(Map.of("artifactId", "NEST4001")))),
                "resultMap", Map.of("artifactRefs", List.of(Map.of("resourceKey", "MAP5001"))));

        assertEquals(List.of("A1001", "FILE2001", "FILE3001", "NEST4001", "MAP5001"),
                AgentToolOutputProjector.extractArtifactIds(result));
        assertTrue(AgentToolOutputProjector.hasArtifactReferences(result));
    }

    @Test
    void shouldDetectReferencePayloadWithoutTreatingPlainTitleAsFile() {
        assertFalse(AgentToolOutputProjector.hasArtifactReferences(Map.of(
                "title", "Draft title",
                "summary", "Plain text result")));
        assertFalse(AgentToolOutputProjector.hasArtifactReferences(Map.of(
                "fileRefs", List.of(Map.of("title", "Draft title")))));
        assertTrue(AgentToolOutputProjector.hasArtifactReferences(Map.of(
                "result", Map.of(
                        "primaryFileName", "report.md",
                        "ossUrl", "/files/report.md"))));
    }

    @Test
    void shouldBuildCallResultFromStructuredOutput() {
        AgentToolStructuredOutput output = AgentToolStructuredOutput.builder(AgentToolOutputNames.REPORT_TOOL)
                .summary("报告已生成")
                .addFileRef(AgentToolFileRef.builder().artifactId("A2001").fileName("report.md").build())
                .build();

        AgentToolCallResult result = AgentToolOutputProjector.toCallResult(output, "report/build", 12L);

        assertTrue(result.isSuccess());
        assertEquals("report_tool", result.getToolName());
        assertEquals(List.of("A2001"), result.getArtifactIds());
    }
}















