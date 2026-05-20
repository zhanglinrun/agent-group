package com.linrun.trigger.http;

import com.linrun.domain.conversation.adapter.GuideStreamControlRepository;
import com.linrun.domain.conversation.service.GuideImageInputService;
import com.linrun.trigger.service.AgentGuideStreamService;
import com.linrun.trigger.service.GuideImageUploadService;
import com.linrun.types.enums.ResponseCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentGuideControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AgentGuideController(
                    testExecutor(),
                    mock(AgentGuideStreamService.class),
                    new GuideImageUploadService(new GuideImageInputService()),
                    new NoopGuideStreamControlRepository()))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void shouldUploadGuideImageForMultimodalInput() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "student-pad-group-price.png",
                "image/png",
                png(16, 16));

        mockMvc.perform(multipart("/api/v1/agent/guide/image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResponseCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.imageName").value("student-pad-group-price.png"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.imageUrl").value(startsWith("data:image/png;base64,")))
                .andExpect(jsonPath("$.data.imageSummary").value(startsWith("图片疑似平板商品或商品截图")));
    }

    private ThreadPoolExecutor testExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    private byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.CYAN);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private static class NoopGuideStreamControlRepository implements GuideStreamControlRepository {

        @Override
        public void markStopped(String sessionId) {
        }

        @Override
        public boolean isStopped(String sessionId) {
            return false;
        }

        @Override
        public void clearStopped(String sessionId) {
        }
    }
}
