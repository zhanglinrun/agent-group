package com.linrun.trigger.http;

import com.linrun.api.dto.AcademicWorkspaceDataHistoryResponse;
import com.linrun.api.dto.AcademicWorkspaceDataCatalogResponse;
import com.linrun.api.dto.AcademicWorkspaceDataRunRequest;
import com.linrun.api.dto.AcademicWorkspaceDataRunResponse;
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
@RequestMapping("/api/v1/academic/workspace/data")
public class AcademicWorkspaceDataController {

    private final AcademicWorkspaceDataService workspaceDataService;

    public AcademicWorkspaceDataController(AcademicWorkspaceDataService workspaceDataService) {
        this.workspaceDataService = workspaceDataService;
    }

    @PostMapping("/run")
    public Response<AcademicWorkspaceDataRunResponse> run(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody(required = false) AcademicWorkspaceDataRunRequest request) {
        return Response.success(workspaceDataService.run(token, request), RequestTraceContext.getRequestId());
    }

    @GetMapping("/history")
    public Response<AcademicWorkspaceDataHistoryResponse> history(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "20") int limit) {
        return Response.success(workspaceDataService.history(token, sessionId, limit), RequestTraceContext.getRequestId());
    }

    @GetMapping("/catalog")
    public Response<AcademicWorkspaceDataCatalogResponse> catalog(
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Response.success(workspaceDataService.catalog(token), RequestTraceContext.getRequestId());
    }
}
