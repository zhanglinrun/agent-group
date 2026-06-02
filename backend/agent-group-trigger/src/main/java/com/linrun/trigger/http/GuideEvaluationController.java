package com.linrun.trigger.http;

import com.linrun.api.dto.GuideEvaluationReportResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.http.GuideEvaluationTriggerHandler;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/evaluate/agent")
public class GuideEvaluationController {

    private final GuideEvaluationTriggerHandler guideEvaluationTriggerService;

    public GuideEvaluationController(GuideEvaluationTriggerHandler guideEvaluationTriggerService) {
        this.guideEvaluationTriggerService = guideEvaluationTriggerService;
    }

    @PostMapping("/run")
    public Response<GuideEvaluationReportResponse> runAgentEvaluation() {
        return Response.success(guideEvaluationTriggerService.runGuideEvaluation(), RequestTraceContext.getRequestId());
    }

    @GetMapping("/latest")
    public Response<GuideEvaluationReportResponse> latestAgentEvaluation() {
        return Response.success(guideEvaluationTriggerService.queryLatestReport(), RequestTraceContext.getRequestId());
    }
}
