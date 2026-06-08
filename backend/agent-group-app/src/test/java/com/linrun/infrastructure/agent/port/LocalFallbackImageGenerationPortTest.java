package com.linrun.infrastructure.agent.port;

import com.linrun.domain.academic.runtime.tool.port.AcademicImageGenerationPort;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFallbackImageGenerationPortTest {

    @Test
    void shouldRenderDataUrlPngAndMarkFallback() {
        LocalFallbackImageGenerationPort port = new LocalFallbackImageGenerationPort();

        AcademicImageGenerationPort.AcademicImageGenerationResult result = port.generate(
                new AcademicImageGenerationPort.AcademicImageGenerationRequest(
                        "支付成功，等待成团，成团到账，退款回滚",
                        "generate",
                        "1024x1024",
                        1,
                        List.of(),
                        List.of()));

        assertTrue(result.success());
        assertEquals("local-fallback-renderer", result.provider());
        assertTrue(result.usedFallback());
        assertEquals(1, result.fileRefs().size());
        String previewUrl = result.fileRefs().getFirst().getPreviewUrl();
        assertTrue(previewUrl.startsWith("data:image/png;base64,"));
        byte[] png = Base64.getDecoder().decode(previewUrl.substring("data:image/png;base64,".length()));
        assertFalse(png.length == 0);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);
        assertEquals((byte) 0x4E, png[2]);
        assertEquals((byte) 0x47, png[3]);
    }
}
