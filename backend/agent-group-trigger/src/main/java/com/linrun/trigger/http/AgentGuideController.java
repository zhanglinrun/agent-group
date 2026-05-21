package com.linrun.trigger.http;

import com.linrun.api.agent.request.GuideStreamRequest;
import com.linrun.api.agent.response.GuideImageUploadResponse;
import com.linrun.api.agent.response.GuideStreamEvent;
import com.linrun.domain.conversation.adapter.GuideStreamControlRepository;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.service.GuideImageUploadService;
import com.linrun.trigger.service.AgentGuideStreamService;
import com.linrun.types.response.Response;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 导购流式接口。
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/agent")
public class AgentGuideController {

    private final ThreadPoolExecutor streamExecutor;
    private final AgentGuideStreamService agentGuideStreamService;
    private final GuideImageUploadService guideImageUploadService;
    private final GuideStreamControlRepository guideStreamControlRepository;

    public AgentGuideController(ThreadPoolExecutor streamExecutor,
                                AgentGuideStreamService agentGuideStreamService,
                                GuideImageUploadService guideImageUploadService,
                                GuideStreamControlRepository guideStreamControlRepository) {
        this.streamExecutor = streamExecutor;
        this.agentGuideStreamService = agentGuideStreamService;
        this.guideImageUploadService = guideImageUploadService;
        this.guideStreamControlRepository = guideStreamControlRepository;
    }

    @PostMapping(value = "/guide/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<GuideImageUploadResponse> uploadGuideImage(@RequestParam("file") MultipartFile file) {
        return Response.success(guideImageUploadService.uploadImage(file), RequestTraceContext.getRequestId());
    }

    @PostMapping(value = "/guide/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter guideStream(@RequestBody GuideStreamRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        streamExecutor.submit(() -> doGuideStream(request, emitter));
        return emitter;
    }

    @PostMapping("/stop")
    public Response<Boolean> stop(@RequestBody Map<String, String> request) {
        String sessionId = request == null ? "" : request.get("sessionId");
        if (StringUtils.hasText(sessionId)) {
            guideStreamControlRepository.markStopped(sessionId);
        }
        return Response.success(Boolean.TRUE, RequestTraceContext.getRequestId());
    }

    private void doGuideStream(GuideStreamRequest request, SseEmitter emitter) {
        String sessionId = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId()
                : "S" + System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        guideStreamControlRepository.clearStopped(sessionId);

        try {
            agentGuideStreamService.streamEvents(
                    request,
                    sessionId,
                    requestId,
                    event -> {
                        if (guideStreamControlRepository.isStopped(sessionId)) {
                            return;
                        }
                        try {
                            send(emitter, event);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    },
                    () -> guideStreamControlRepository.isStopped(sessionId));
            guideStreamControlRepository.clearStopped(sessionId);
            emitter.complete();
        } catch (UncheckedIOException e) {
            emitter.completeWithError(e.getCause());
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private void send(SseEmitter emitter, GuideStreamEvent<?> event) throws IOException {
        emitter.send(SseEmitter.event().name(event.getEvent()).data(event, MediaType.APPLICATION_JSON));
        pause();
    }

    private void pause() {
        try {
            Thread.sleep(350L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
