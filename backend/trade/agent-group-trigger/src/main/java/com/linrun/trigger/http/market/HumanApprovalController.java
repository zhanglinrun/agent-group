package com.linrun.trigger.http.market;

import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/market/approval")
public class HumanApprovalController {

    private final HumanApprovalHandler humanApprovalHandler;

    public HumanApprovalController(HumanApprovalHandler humanApprovalHandler) {
        this.humanApprovalHandler = humanApprovalHandler;
    }

    @PostMapping
    public Response<Map<String, Object>> create(@RequestBody(required = false) Map<String, Object> request) {
        return Response.success(humanApprovalHandler.createApproval(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/{approvalId}/approve")
    public Response<Map<String, Object>> approve(@PathVariable String approvalId,
                                                 @RequestBody(required = false) Map<String, Object> request) {
        return Response.success(humanApprovalHandler.approve(approvalId, request), RequestTraceContext.getRequestId());
    }
}
