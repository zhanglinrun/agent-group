package com.linrun.trigger.http.agent;

import com.linrun.api.dto.AgentWorkspaceDataHistoryResponse;
import com.linrun.api.dto.AgentWorkspaceDataCatalogResponse;
import com.linrun.api.dto.AgentWorkspaceDataRunRequest;
import com.linrun.api.dto.AgentWorkspaceDataRunResponse;
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
@RequestMapping("/api/v1/agent/workspaces/data")
public class AgentWorkspaceDataController {

    private final AgentWorkspaceDataService workspaceDataService;

    public AgentWorkspaceDataController(AgentWorkspaceDataService workspaceDataService) {
        this.workspaceDataService = workspaceDataService;
    }

    @PostMapping("/run")
    public Response<AgentWorkspaceDataRunResponse> run(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody(required = false) AgentWorkspaceDataRunRequest request) {
        return Response.success(workspaceDataService.run(token, request), RequestTraceContext.getRequestId());
    }

    @GetMapping("/history")
    public Response<AgentWorkspaceDataHistoryResponse> history(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "20") int limit) {
        return Response.success(workspaceDataService.history(token, sessionId, limit), RequestTraceContext.getRequestId());
    }

    @GetMapping("/catalog")
    public Response<AgentWorkspaceDataCatalogResponse> catalog(
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Response.success(workspaceDataService.catalog(token), RequestTraceContext.getRequestId());
    }
}















