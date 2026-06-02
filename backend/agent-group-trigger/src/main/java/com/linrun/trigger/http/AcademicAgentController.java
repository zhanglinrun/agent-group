package com.linrun.trigger.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AcademicAgentStreamRequest;
import com.linrun.api.dto.AcademicFileUploadResponse;
import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.api.dto.AcademicSessionSummaryDTO;
import com.linrun.api.dto.GuideStreamEvent;
import com.linrun.domain.agent.conversation.adapter.GuideStreamControlRepository;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/academic")
public class AcademicAgentController {

    private final AcademicBearDoctorAgentHandler academicBearDoctorAgentHandler;
    private final GuideStreamControlRepository guideStreamControlRepository;
    private final ObjectMapper objectMapper;

    public AcademicAgentController(AcademicBearDoctorAgentHandler academicBearDoctorAgentHandler,
                                   GuideStreamControlRepository guideStreamControlRepository,
                                   ObjectMapper objectMapper) {
        this.academicBearDoctorAgentHandler = academicBearDoctorAgentHandler;
        this.guideStreamControlRepository = guideStreamControlRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> stream(@RequestHeader(value = "Authorization", required = false) String token,
                               @RequestBody(required = false) AcademicAgentStreamRequest request) {
        AcademicAgentStreamRequest safeRequest = request == null ? new AcademicAgentStreamRequest() : request;
        return startStream(token, safeRequest);
    }

    @PostMapping(value = "/resume", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> resume(@RequestHeader(value = "Authorization", required = false) String token,
                               @RequestBody Map<String, String> request) {
        String sessionId = request == null ? "" : request.get("sessionId");
        AcademicAgentStreamRequest resumeRequest = academicBearDoctorAgentHandler.resumeRequest(token, sessionId);
        return startStream(token, resumeRequest);
    }

    @GetMapping("/task/status")
    public Response<Map<String, Object>> taskStatus(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam String sessionId) {
        Map<String, Object> status = academicBearDoctorAgentHandler.queryTaskStatus(token, sessionId);
        status.put("stopped", guideStreamControlRepository.isStopped(sessionId));
        return Response.success(status, RequestTraceContext.getRequestId());
    }

    private Flux<String> startStream(String token, AcademicAgentStreamRequest request) {
        AcademicAgentStreamRequest safeRequest = request == null ? new AcademicAgentStreamRequest() : request;
        String sessionId = StringUtils.hasText(safeRequest.getSessionId())
                ? safeRequest.getSessionId()
                : "AS" + System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        guideStreamControlRepository.clearStopped(sessionId);
        return academicBearDoctorAgentHandler.streamEventFlux(
                        token,
                        safeRequest,
                        sessionId,
                        requestId)
                .map(this::toJson);
    }

    @PostMapping(value = "/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<AcademicFileUploadResponse> upload(@RequestHeader(value = "Authorization", required = false) String token,
                                                       @RequestParam("file") MultipartFile file,
                                                       @RequestParam(required = false) String sessionId) {
        return Response.success(academicBearDoctorAgentHandler.upload(token, file, sessionId), RequestTraceContext.getRequestId());
    }

    @PostMapping("/stop")
    public Response<Boolean> stop(@RequestHeader(value = "Authorization", required = false) String token,
                                  @RequestBody Map<String, String> request) {
        String sessionId = request == null ? "" : request.get("sessionId");
        boolean stopped = true;
        if (StringUtils.hasText(sessionId)) {
            guideStreamControlRepository.markStopped(sessionId);
            stopped = academicBearDoctorAgentHandler.stop(token, sessionId);
        }
        return Response.success(stopped, RequestTraceContext.getRequestId());
    }

    @GetMapping("/sessions")
    public Response<List<AcademicSessionSummaryDTO>> sessions(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "20") int limit) {
        return Response.success(academicBearDoctorAgentHandler.querySessions(token, limit), RequestTraceContext.getRequestId());
    }

    @GetMapping("/sessions/{sessionId}")
    public Response<AcademicSessionDetailResponse> detail(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String sessionId) {
        return Response.success(academicBearDoctorAgentHandler.queryDetail(token, sessionId), RequestTraceContext.getRequestId());
    }

    private String toJson(GuideStreamEvent<?> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("学术智能体流事件序列化失败", e);
        }
    }
}
