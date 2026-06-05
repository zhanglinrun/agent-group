package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.port.AcademicImageGenerationPort;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicImageGenerationToolRuntimeTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldGenerateImageThroughPortAndExposeFileRefs() {
        AcademicImageGenerationPort port = request ->
                new AcademicImageGenerationPort.AcademicImageGenerationResult(
                        true,
                        "mock-image",
                        "生成一张拼团活动图",
                        false,
                        List.of(AcademicToolFileRef.builder()
                                .artifactId("A-IMG-1")
                                .fileName("poster.png")
                                .downloadUrl("/artifacts/A-IMG-1")
                                .contentType("image/png")
                                .build()),
                        "");
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.registerStructured(AcademicImageGenerationToolRuntime.definition(),
                new AcademicImageGenerationToolRuntime(port)::call);

        AcademicToolCallResult result = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.IMAGE_GENERATION)
                .arguments(Map.of(
                        "prompt", "生成拼团活动主图",
                        "size", "1024x1024",
                        "batchCount", 1))
                .build());

        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        assertTrue(result.isSuccess());
        assertEquals("mock-image", metadata.get("provider"));
        assertEquals("1024x1024", metadata.get("size"));
        assertEquals(List.of("A-IMG-1"), result.getArtifactIds());
    }

    @Test
    void shouldSurfaceProviderFailure() {
        AcademicImageGenerationPort port = request ->
                new AcademicImageGenerationPort.AcademicImageGenerationResult(
                        false, "mock-image", "", false, List.of(), "quota exhausted");
        AcademicImageGenerationToolRuntime runtime = new AcademicImageGenerationToolRuntime(port);

        AppException exception = assertThrows(AppException.class,
                () -> runtime.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.IMAGE_GENERATION)
                        .arguments(Map.of("prompt", "生成图片"))
                        .build()));

        assertEquals("IMAGE_0003", exception.getCode());
    }
}
