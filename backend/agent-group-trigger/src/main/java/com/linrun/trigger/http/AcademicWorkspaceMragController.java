package com.linrun.trigger.http;

import com.linrun.api.dto.AcademicWorkspaceMragHistoryResponse;
import com.linrun.api.dto.AcademicWorkspaceMragRunRequest;
import com.linrun.api.dto.AcademicWorkspaceMragRunResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/academic/workspace/mrag")
public class AcademicWorkspaceMragController {

    private final AcademicWorkspaceMragService workspaceMragService;

    public AcademicWorkspaceMragController(AcademicWorkspaceMragService workspaceMragService) {
        this.workspaceMragService = workspaceMragService;
    }

    @PostMapping("/run")
    public Response<AcademicWorkspaceMragRunResponse> run(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody(required = false) AcademicWorkspaceMragRunRequest request) {
        return Response.success(workspaceMragService.run(token, request), RequestTraceContext.getRequestId());
    }

    @GetMapping("/history")
    public Response<AcademicWorkspaceMragHistoryResponse> history(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "20") int limit) {
        return Response.success(workspaceMragService.history(token, sessionId, limit), RequestTraceContext.getRequestId());
    }
}
