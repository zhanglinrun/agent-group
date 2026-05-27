package com.linrun.trigger.http;

import com.linrun.api.agent.request.ApproveHumanApprovalRequest;
import com.linrun.api.agent.request.CreateHumanApprovalRequest;
import com.linrun.api.agent.response.HumanApprovalResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.service.HumanApprovalService;
import com.linrun.types.response.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/agent/hitl")
public class HumanApprovalController {

    private final HumanApprovalService humanApprovalService;

    public HumanApprovalController(HumanApprovalService humanApprovalService) {
        this.humanApprovalService = humanApprovalService;
    }

    @PostMapping("/create")
    public Response<HumanApprovalResponse> create(@RequestBody CreateHumanApprovalRequest request) {
        return Response.success(humanApprovalService.createApproval(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/approve")
    public Response<HumanApprovalResponse> approve(@RequestBody ApproveHumanApprovalRequest request) {
        return Response.success(humanApprovalService.approve(request), RequestTraceContext.getRequestId());
    }

    @GetMapping("/query")
    public Response<HumanApprovalResponse> query(@RequestParam String approvalId) {
        return Response.success(humanApprovalService.queryApproval(approvalId), RequestTraceContext.getRequestId());
    }
}
