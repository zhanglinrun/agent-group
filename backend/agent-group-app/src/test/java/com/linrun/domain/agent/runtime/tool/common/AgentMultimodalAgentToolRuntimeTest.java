package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolCallResult;
import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.port.AgentMultimodalAnalysisPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMultimodalAgentToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldAnalyzeTextAndImagesThroughPort() {
        AgentMultimodalAnalysisPort port = request ->
                new AgentMultimodalAnalysisPort.AgentMultimodalAnalysisResult(
                        true,
                        "识别到一张交易流程图",
                        "图中包含支付、成团、额度发放三个节点。",
                        Map.of("diagramType", "trade_flow"),
                        List.of(),
                        "");
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.registerStructured(AgentMultimodalAgentToolRuntime.definition(),
                new AgentMultimodalAgentToolRuntime(port)::call);

        AgentToolCallResult result = registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.MULTIMODAL_AGENT)
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
        AgentMultimodalAgentToolRuntime runtime = new AgentMultimodalAgentToolRuntime(null);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.MULTIMODAL_AGENT)
                        .arguments(Map.of("task", "分析图片"))
                        .build()));

        assertEquals("MULTIMODAL_0001", exception.getCode());
    }
}















