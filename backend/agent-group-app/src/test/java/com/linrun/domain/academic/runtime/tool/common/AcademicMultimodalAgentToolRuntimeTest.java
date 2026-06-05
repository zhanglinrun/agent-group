package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.port.AcademicMultimodalAnalysisPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicMultimodalAgentToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldAnalyzeTextAndImagesThroughPort() {
        AcademicMultimodalAnalysisPort port = request ->
                new AcademicMultimodalAnalysisPort.AcademicMultimodalAnalysisResult(
                        true,
                        "识别到一张交易流程图",
                        "图中包含支付、成团、额度发放三个节点。",
                        Map.of("diagramType", "trade_flow"),
                        List.of(),
                        "");
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.registerStructured(AcademicMultimodalAgentToolRuntime.definition(),
                new AcademicMultimodalAgentToolRuntime(port)::call);

        AcademicToolCallResult result = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.MULTIMODAL_AGENT)
                .arguments(Map.of(
                        "task", "分析交易流程图",
                        "text", "请检查流程是否缺少退款",
                        "imageUrls", List.of("/files/flow.png")))
                .build());

        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        assertTrue(result.isSuccess());
        assertEquals("trade_flow", metadata.get("diagramType"));
        assertEquals(1, metadata.get("imageCount"));
        assertTrue(String.valueOf(result.getResult().get("content")).contains("额度发放"));
    }

    @Test
    void shouldRejectWhenPortMissing() {
        AcademicMultimodalAgentToolRuntime runtime = new AcademicMultimodalAgentToolRuntime(null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.MULTIMODAL_AGENT)
                        .arguments(Map.of("task", "分析图片"))
                        .build()));

        assertEquals("MULTIMODAL_0001", exception.getCode());
    }
}
