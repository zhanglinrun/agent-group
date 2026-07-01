package com.linrun.trigger.http.agent;

import com.linrun.api.dto.AgentWorkspaceCreateRequest;
import com.linrun.api.dto.AgentWorkspaceFileBindRequest;
import com.linrun.api.dto.AgentWorkspacePatchCreateRequest;
import com.linrun.api.dto.AgentWorkspaceResponse;
import com.linrun.domain.agent.workspace.service.AgentWorkspaceService;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/agent/workspaces")
public class AgentWorkspaceController {

    private final UserAccountService userAccountService;
    private final AgentWorkspaceService agentWorkspaceService;

    public AgentWorkspaceController(UserAccountService userAccountService,
                                     AgentWorkspaceService agentWorkspaceService) {
        this.userAccountService = userAccountService;
        this.agentWorkspaceService = agentWorkspaceService;
    }

    @PostMapping
    public Response<AgentWorkspaceResponse> create(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody(required = false) AgentWorkspaceCreateRequest request) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(agentWorkspaceService.createProject(user.getUserId(), request),
                RequestTraceContext.getRequestId());
    }

    @GetMapping
    public Response<List<AgentWorkspaceResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "20") int limit) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(agentWorkspaceService.queryProjects(user.getUserId(), limit),
                RequestTraceContext.getRequestId());
    }

    @GetMapping("/{projectId}")
    public Response<AgentWorkspaceResponse> detail(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String projectId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(agentWorkspaceService.queryProject(user.getUserId(), projectId),
                RequestTraceContext.getRequestId());
    }

    @PostMapping("/{projectId}/files")
    public Response<AgentWorkspaceResponse> bindFile(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String projectId,
            @RequestBody(required = false) AgentWorkspaceFileBindRequest request) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(agentWorkspaceService.bindFile(user.getUserId(), projectId, request),
                RequestTraceContext.getRequestId());
    }

    @PostMapping("/{projectId}/patches")
    public Response<AgentWorkspaceResponse> proposePatch(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String projectId,
            @RequestBody(required = false) AgentWorkspacePatchCreateRequest request) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(agentWorkspaceService.proposePatch(user.getUserId(), projectId, request),
                RequestTraceContext.getRequestId());
    }

    @PostMapping("/{projectId}/patches/{patchId}/apply")
    public Response<AgentWorkspaceResponse> applyPatch(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String projectId,
            @PathVariable String patchId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(agentWorkspaceService.applyPatch(user.getUserId(), projectId, patchId),
                RequestTraceContext.getRequestId());
    }
}















