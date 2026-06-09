package com.linrun.trigger.http.agent;

import com.linrun.api.dto.AcademicWorkspaceImageGenerateRequest;
import com.linrun.api.dto.AcademicWorkspaceImageGenerateResponse;
import com.linrun.api.dto.AcademicWorkspaceImageHistoryResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/academic/workspace/image")
public class AcademicWorkspaceImageController {

    private final AcademicWorkspaceImageService workspaceImageService;

    public AcademicWorkspaceImageController(AcademicWorkspaceImageService workspaceImageService) {
        this.workspaceImageService = workspaceImageService;
    }

    @PostMapping("/generate")
    public Response<AcademicWorkspaceImageGenerateResponse> generate(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody(required = false) AcademicWorkspaceImageGenerateRequest request) {
        return Response.success(workspaceImageService.generate(token, request), RequestTraceContext.getRequestId());
    }

    @GetMapping("/history")
    public Response<AcademicWorkspaceImageHistoryResponse> history(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "20") int limit) {
        return Response.success(workspaceImageService.history(token, sessionId, limit), RequestTraceContext.getRequestId());
    }
}















