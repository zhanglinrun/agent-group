package com.linrun.trigger.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.GuideEventType;
import com.linrun.api.dto.AnswerDeltaDTO;
import com.linrun.api.dto.GuideStreamEvent;
import com.linrun.domain.agent.conversation.adapter.GuideStreamControlRepository;
import com.linrun.domain.agent.conversation.service.GuideImageInputService;
import com.linrun.trigger.http.AgentGuideStreamHandler;
import com.linrun.trigger.http.GuideImageUploadHandler;
import com.linrun.types.enums.ResponseCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentGuideControllerTest {

    private final AgentGuideStreamHandler agentGuideStreamService = mock(AgentGuideStreamHandler.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AgentGuideController(
                    agentGuideStreamService,
                    new GuideImageUploadHandler(new GuideImageInputService()),
                    new NoopGuideStreamControlRepository(),
                    new ObjectMapper()))
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

    @Test
    void shouldReturnFluxTextEventStreamForGuideStream() throws Exception {
        GuideStreamEvent<AnswerDeltaDTO> event = GuideStreamEvent.of(
                GuideEventType.ANSWER_DELTA.getCode(),
                "S10001",
                "R10001",
                1,
                new AnswerDeltaDTO("第一段"));
        when(agentGuideStreamService.streamEventFlux(any(), anyString(), anyString(), any()))
                .thenReturn(Flux.just(event));

        MvcResult result = mockMvc.perform(post("/api/v1/agent/guide/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"sessionId\":\"S10001\",\"question\":\"推荐一款学习平板\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data:")))
                .andExpect(content().string(containsString("answer_delta")))
                .andExpect(content().string(containsString("\"content\"")));
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
