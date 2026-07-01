package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolCallResult;
import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.port.AgentImageGenerationPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentImageGenerationToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldGenerateImageThroughPortAndExposeFileRefs() {
        AgentImageGenerationPort port = request ->
                new AgentImageGenerationPort.AgentImageGenerationResult(
                        true,
                        "mock-image",
                        "生成一张拼团活动图",
                        false,
                        List.of(AgentToolFileRef.builder()
                                .artifactId("A-IMG-1")
                                .fileName("poster.png")
                                .downloadUrl("/artifacts/A-IMG-1")
                                .contentType("image/png")
                                .build()),
                        "");
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.registerStructured(AgentImageGenerationToolRuntime.definition(),
                new AgentImageGenerationToolRuntime(port)::call);

        AgentToolCallResult result = registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.IMAGE_GENERATION)
                .arguments(Map.of(
                        "prompt", "生成拼团活动主图",
                        "model", "gpt-image-2",
                        "quality", "auto",
                        "aspectRatio", "1:1",
                        "size", "1024x1024",
                        "batchCount", 1))
                .build());

        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        assertTrue(result.isSuccess());
        assertEquals("mock-image", metadata.get("provider"));
        assertEquals("gpt-image-2", metadata.get("model"));
        assertEquals("auto", metadata.get("quality"));
        assertEquals("1:1", metadata.get("aspectRatio"));
        assertEquals("1024x1024", metadata.get("size"));
        assertEquals(List.of("A-IMG-1"), result.getArtifactIds());
    }

    @Test
    void shouldSurfaceProviderFailure() {
        AgentImageGenerationPort port = request ->
                new AgentImageGenerationPort.AgentImageGenerationResult(
                        false, "mock-image", "", false, List.of(), "quota exhausted");
        AgentImageGenerationToolRuntime runtime = new AgentImageGenerationToolRuntime(port);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AgentToolCallCommand.builder(AgentToolOutputNames.IMAGE_GENERATION)
                        .arguments(Map.of("prompt", "生成图片"))
                        .build()));

        assertEquals("IMAGE_0003", exception.getCode());
    }
}















