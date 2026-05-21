package com.linrun.trigger.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

/**
 * 导购流式接口。
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/agent")
public class AgentGuideController {

    private final AgentGuideStreamService agentGuideStreamService;
    private final GuideImageUploadService guideImageUploadService;
    private final GuideStreamControlRepository guideStreamControlRepository;
    private final ObjectMapper objectMapper;

    public AgentGuideController(AgentGuideStreamService agentGuideStreamService,
                                GuideImageUploadService guideImageUploadService,
                                GuideStreamControlRepository guideStreamControlRepository,
                                ObjectMapper objectMapper) {
        this.agentGuideStreamService = agentGuideStreamService;
        this.guideImageUploadService = guideImageUploadService;
        this.guideStreamControlRepository = guideStreamControlRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/guide/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<GuideImageUploadResponse> uploadGuideImage(@RequestParam("file") MultipartFile file) {
        return Response.success(guideImageUploadService.uploadImage(file), RequestTraceContext.getRequestId());
    }

    @PostMapping(value = "/guide/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> guideStream(@RequestBody GuideStreamRequest request) {
        GuideStreamRequest safeRequest = request == null ? new GuideStreamRequest() : request;
        String sessionId = StringUtils.hasText(safeRequest.getSessionId())
                ? safeRequest.getSessionId()
                : "S" + System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        guideStreamControlRepository.clearStopped(sessionId);
        return agentGuideStreamService.streamEventFlux(
                        safeRequest,
                        sessionId,
                        requestId,
                        () -> guideStreamControlRepository.isStopped(sessionId))
                .map(this::toJson)
                .doFinally(signalType -> guideStreamControlRepository.clearStopped(sessionId));
    }

    @PostMapping("/stop")
    public Response<Boolean> stop(@RequestBody Map<String, String> request) {
        String sessionId = request == null ? "" : request.get("sessionId");
        if (StringUtils.hasText(sessionId)) {
            guideStreamControlRepository.markStopped(sessionId);
        }
        return Response.success(Boolean.TRUE, RequestTraceContext.getRequestId());
    }

    private String toJson(GuideStreamEvent<?> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("导购流事件序列化失败", e);
        }
    }
}
