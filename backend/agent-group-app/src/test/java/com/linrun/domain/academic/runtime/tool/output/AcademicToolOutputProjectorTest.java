package com.linrun.domain.academic.runtime.tool.output;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicToolOutputProjectorTest {

    @Test
    void shouldProjectStructuredOutputToResultMapAndArtifactIds() {
        AcademicToolStructuredOutput output = AcademicToolStructuredOutput.builder(AcademicToolOutputNames.IMAGE_GENERATION)
                .title("配图生成")
                .summary("生成两张活动海报")
                .content("图片已生成")
                .putMetadata("prompt", "秋招项目展示")
                .addFileRef(AcademicToolFileRef.builder()
                        .artifactId("A1001")
                        .fileName("poster.png")
                        .downloadUrl("/artifacts/A1001")
                        .contentType("image/png")
                        .fileSize(1024)
                        .build())
                .build();

        Map<String, Object> result = AcademicToolOutputProjector.toResultMap(output);

        assertTrue(AcademicToolOutputNames.isRichTool(AcademicToolOutputNames.IMAGE_GENERATION));
        assertEquals("配图生成", result.get("title"));
        assertEquals(List.of("A1001"), AcademicToolOutputProjector.extractArtifactIds(result));
        assertEquals("生成两张活动海报", AcademicToolOutputProjector.summarize(output));
    }

    @Test
    void shouldBuildCallResultFromStructuredOutput() {
        AcademicToolStructuredOutput output = AcademicToolStructuredOutput.builder(AcademicToolOutputNames.REPORT_TOOL)
                .summary("报告已生成")
                .addFileRef(AcademicToolFileRef.builder().artifactId("A2001").fileName("report.md").build())
                .build();

        AcademicToolCallResult result = AcademicToolOutputProjector.toCallResult(output, "report/build", 12L);

        assertTrue(result.isSuccess());
        assertEquals("report_tool", result.getToolName());
        assertEquals(List.of("A2001"), result.getArtifactIds());
    }
}
