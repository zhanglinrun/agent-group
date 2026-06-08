package com.linrun.trigger.http.agent;

import com.linrun.api.dto.AcademicProjectCreateRequest;
import com.linrun.api.dto.AcademicProjectFileBindRequest;
import com.linrun.api.dto.AcademicProjectPatchCreateRequest;
import com.linrun.api.dto.AcademicProjectResponse;
import com.linrun.domain.academic.project.service.AcademicProjectService;
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
@RequestMapping("/api/v1/academic/projects")
public class AcademicProjectController {

    private final UserAccountService userAccountService;
    private final AcademicProjectService academicProjectService;

    public AcademicProjectController(UserAccountService userAccountService,
                                     AcademicProjectService academicProjectService) {
        this.userAccountService = userAccountService;
        this.academicProjectService = academicProjectService;
    }

    @PostMapping
    public Response<AcademicProjectResponse> create(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestBody(required = false) AcademicProjectCreateRequest request) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(academicProjectService.createProject(user.getUserId(), request),
                RequestTraceContext.getRequestId());
    }

    @GetMapping
    public Response<List<AcademicProjectResponse>> list(
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(defaultValue = "20") int limit) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(academicProjectService.queryProjects(user.getUserId(), limit),
                RequestTraceContext.getRequestId());
    }

    @GetMapping("/{projectId}")
    public Response<AcademicProjectResponse> detail(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String projectId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(academicProjectService.queryProject(user.getUserId(), projectId),
                RequestTraceContext.getRequestId());
    }

    @PostMapping("/{projectId}/files")
    public Response<AcademicProjectResponse> bindFile(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String projectId,
            @RequestBody(required = false) AcademicProjectFileBindRequest request) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(academicProjectService.bindFile(user.getUserId(), projectId, request),
                RequestTraceContext.getRequestId());
    }

    @PostMapping("/{projectId}/patches")
    public Response<AcademicProjectResponse> proposePatch(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String projectId,
            @RequestBody(required = false) AcademicProjectPatchCreateRequest request) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(academicProjectService.proposePatch(user.getUserId(), projectId, request),
                RequestTraceContext.getRequestId());
    }

    @PostMapping("/{projectId}/patches/{patchId}/apply")
    public Response<AcademicProjectResponse> applyPatch(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable String projectId,
            @PathVariable String patchId) {
        UserAccount user = userAccountService.requireUserByToken(token);
        return Response.success(academicProjectService.applyPatch(user.getUserId(), projectId, patchId),
                RequestTraceContext.getRequestId());
    }
}
