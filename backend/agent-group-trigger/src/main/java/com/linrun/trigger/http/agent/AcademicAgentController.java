package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AcademicAgentStreamRequest;
import com.linrun.api.dto.AcademicFileUploadResponse;
import com.linrun.api.dto.AcademicReplayResponse;
import com.linrun.api.dto.AcademicRunDetailResponse;
import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.api.dto.AcademicSessionSummaryDTO;
import com.linrun.api.dto.AgentDiagnosisReportDTO;
import com.linrun.api.dto.QuotaStreamEvent;
import com.linrun.domain.agent.conversation.adapter.QuotaStreamControlRepository;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/academic")
public class AcademicAgentController {

    private final AcademicAgentHandler academicAgentHandler;
    private final QuotaStreamControlRepository quotaStreamControlRepository;
    private final ObjectMapper objectMapper;

    public AcademicAgentController(AcademicAgentHandler academicAgentHandler,
                                   QuotaStreamControlRepository quotaStreamControlRepository,
                                   ObjectMapper objectMapper) {
        this.academicAgentHandler = academicAgentHandler;
        this.quotaStreamControlRepository = quotaStreamControlRepository;
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
                               @RequestBody(required = false) AcademicAgentStreamRequest request) {
        String sessionId = request == null ? "" : request.getSessionId();
        AcademicAgentStreamRequest resumeRequest = academicAgentHandler.resumeRequest(token, sessionId);
        copyRuntimeLlmConfig(request, resumeRequest);
        return startStream(token, resumeRequest);
    }

    @GetMapping("/task/status")
    public Response<Map<String, Object>> taskStatus(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam String sessionId) {
        Map<String, Object> status = academicAgentHandler.queryTaskStatus(token, sessionId);
        status.put("stopped", quotaStreamControlRepository.isStopped(sessionId));
        return Response.success(status, RequestTraceContext.getRequestId());
    }

    @GetMapping("/capabilities")
    public Response<Map<String, Object>> capabilities() {
        return Response.success(academicAgentHandler.capabilities(), RequestTraceContext.getRequestId());
    }

    private Flux<String> startStream(String token, AcademicAgentStreamRequest request) {
        AcademicAgentStreamRequest safeRequest = request == null ? new AcademicAgentStreamRequest() : request;
        String sessionId = StringUtils.hasText(safeRequest.getSessionId())
                ? safeRequest.getSessionId()
                : "AS" + System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        quotaStreamControlRepository.clearStopped(sessionId);
        return academicAgentHandler.backgroundStreamEventFlux(
                        token,
                        safeRequest,
                        sessionId,
                        requestId)
                .map(this::toJson);
    }

    @PostMapping(value = "/stream/attach", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> attach(@RequestHeader(value = "Authorization", required = false) String token,
                               @RequestBody(required = false) AcademicAgentStreamRequest request) {
        String sessionId = request == null ? "" : request.getSessionId();
        String requestId = UUID.randomUUID().toString();
        if (!StringUtils.hasText(sessionId)) {
            return Flux.just(toJson(QuotaStreamEvent.of("error", "", requestId, 1,
                    Map.of("code", "0001", "message", "会话编号不能为空"))));
        }
        return academicAgentHandler.attachEventFlux(token, sessionId, requestId)
                .map(this::toJson);
    }

    @PostMapping(value = "/file/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<AcademicFileUploadResponse> upload(@RequestHeader(value = "Authorization", required = false) String token,
                                                       @RequestParam("file") MultipartFile file,
                                                       @RequestParam(required = false) String sessionId) {
        return Response.success(academicAgentHandler.upload(token, file, sessionId), RequestTraceContext.getRequestId());
    }

    @PostMapping("/stop")
    public Response<Boolean> stop(@RequestHeader(value = "Authorization", required = false) String token,
                                  @RequestBody Map<String, String> request) {
        String sessionId = request == null ? "" : request.get("sessionId");
        boolean stopped = true;
        if (StringUtils.hasText(sessionId)) {
            quotaStreamControlRepository.markStopped(sessionId);
            stopped = academicAgentHandler.stop(token, sessionId);
        }
        return Response.success(stopped, RequestTraceContext.getRequestId());
    }

    @GetMapping("/sessions")
    public Response<List<AcademicSessionSummaryDTO>> sessions(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "20") int limit) {
        return Response.success(academicAgentHandler.querySessions(token, limit), RequestTraceContext.getRequestId());
    }

    @GetMapping("/sessions/{sessionId}")
    public Response<AcademicSessionDetailResponse> detail(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String sessionId) {
        return Response.success(academicAgentHandler.queryDetail(token, sessionId), RequestTraceContext.getRequestId());
    }

    @GetMapping("/sessions/{sessionId}/runs")
    public Response<List<AcademicRunDetailResponse.Run>> runs(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "20") int limit) {
        return Response.success(academicAgentHandler.queryRuns(token, sessionId, limit), RequestTraceContext.getRequestId());
    }

    @GetMapping("/sessions/{sessionId}/replay")
    public Response<List<AcademicReplayResponse>> replay(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String sessionId) {
        return Response.success(academicAgentHandler.queryReplay(token, sessionId), RequestTraceContext.getRequestId());
    }

    @GetMapping("/runs/{runId}")
    public Response<AcademicRunDetailResponse> runDetail(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String runId) {
        return Response.success(academicAgentHandler.queryRunDetail(token, runId), RequestTraceContext.getRequestId());
    }

    @GetMapping("/runs/{runId}/diagnosis")
    public Response<AgentDiagnosisReportDTO> runDiagnosis(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String runId) {
        return Response.success(academicAgentHandler.queryRunDiagnosis(token, runId), RequestTraceContext.getRequestId());
    }

    @GetMapping("/runs/{runId}/replay")
    public Response<AcademicReplayResponse> runReplay(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String runId) {
        return Response.success(academicAgentHandler.queryRunReplay(token, runId), RequestTraceContext.getRequestId());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Response<Boolean> deleteSession(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String sessionId) {
        academicAgentHandler.deleteSession(token, sessionId);
        return Response.success(true, RequestTraceContext.getRequestId());
    }

    @PostMapping("/sessions/{sessionId}/rollback")
    public Response<Boolean> rollbackSession(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {
        String messageId = request == null ? "" : request.getOrDefault("messageId", "");
        return Response.success(academicAgentHandler.rollbackSession(token, sessionId, messageId),
                RequestTraceContext.getRequestId());
    }

    @GetMapping("/artifacts/download")
    public ResponseEntity<InputStreamResource> downloadArtifact(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam String sessionId,
            @RequestParam String artifactId) throws Exception {
        AcademicArtifactService.DownloadArtifact artifact =
                academicAgentHandler.downloadArtifact(token, sessionId, artifactId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(artifact.contentType()))
                .contentLength(artifact.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(artifact.fileName()))
                .body(new InputStreamResource(Files.newInputStream(artifact.path())));
    }

    private String attachmentHeader(String fileName) {
        String safeName = StringUtils.hasText(fileName) ? fileName.trim() : "artifact";
        safeName = safeName.replaceAll("[\\r\\n\\\\/]+", "_").replace("\"", "'");
        String fallback = safeName.replaceAll("[^\\x20-\\x7E]", "_");
        String encoded = URLEncoder.encode(safeName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename*=UTF-8''" + encoded + "; filename=\"" + fallback + "\"";
    }

    private void copyRuntimeLlmConfig(AcademicAgentStreamRequest source, AcademicAgentStreamRequest target) {
        if (source == null || target == null) {
            return;
        }
        target.setLlmBaseUrl(source.getLlmBaseUrl());
        target.setLlmApiKey(source.getLlmApiKey());
        target.setLlmModel(source.getLlmModel());
        target.setWebSearchEnabled(source.getWebSearchEnabled());
        target.setOutputStyle(source.getOutputStyle());
        target.setContinueTraceId(source.getContinueTraceId());
    }

    private String toJson(QuotaStreamEvent<?> event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Agent 流事件序列化失败", e);
        }
    }
}












