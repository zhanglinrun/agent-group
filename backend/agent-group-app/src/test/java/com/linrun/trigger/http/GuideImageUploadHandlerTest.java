package com.linrun.trigger.http;







import com.linrun.trigger.support.tool.ToolExecution;
import com.linrun.trigger.support.tool.ToolExecutor;
import com.linrun.domain.trade.service.*;
import com.linrun.domain.trade.service.payment.*;
import com.linrun.domain.trade.service.task.NotifyTaskService;
import com.linrun.domain.support.metrics.AgentObservabilityMetrics;
import com.linrun.api.dto.GuideImageUploadResponse;
import com.linrun.domain.agent.conversation.service.GuideImageInputService;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideImageUploadHandlerTest {

    @Test
    void shouldConvertUploadedImageToMultimodalInput() throws IOException {
        GuideImageUploadHandler service = new GuideImageUploadHandler(new GuideImageInputService());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "student-pad-group-price.png",
                "image/png",
                png(16, 16));

        GuideImageUploadResponse response = service.uploadImage(file);

        assertEquals("student-pad-group-price.png", response.getImageName());
        assertEquals("image/png", response.getContentType());
        assertEquals(file.getSize(), response.getImageSize());
        assertTrue(response.getImageUrl().startsWith("data:image/png;base64,"));
        assertTrue(response.getImageSummary().contains("图片疑似平板商品或商品截图"));
        assertTrue(response.getImageSummary().contains("价格、优惠或拼团信息"));
    }

    @Test
    void shouldStripPathFromUploadedImageName() throws IOException {
        GuideImageUploadHandler service = new GuideImageUploadHandler(new GuideImageInputService());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "..\\unsafe\\student-pad.png",
                "image/png",
                png(16, 16));

        GuideImageUploadResponse response = service.uploadImage(file);

        assertEquals("student-pad.png", response.getImageName());
        assertTrue(response.getImageSummary().contains("student-pad.png"));
    }

    @Test
    void shouldRejectNonImageUpload() {
        GuideImageUploadHandler service = new GuideImageUploadHandler(new GuideImageInputService());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "goods.pdf",
                "application/pdf",
                "fake-pdf".getBytes(StandardCharsets.UTF_8));

        AppException exception = assertThrows(AppException.class, () -> service.uploadImage(file));

        assertEquals("MULTIMODAL_0004", exception.getCode());
    }

    @Test
    void shouldRejectImageThatIsTooSmallForVisionModel() throws IOException {
        GuideImageUploadHandler service = new GuideImageUploadHandler(new GuideImageInputService());
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "tiny.png",
                "image/png",
                png(1, 1));

        AppException exception = assertThrows(AppException.class, () -> service.uploadImage(file));

        assertEquals("MULTIMODAL_0007", exception.getCode());
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
}
