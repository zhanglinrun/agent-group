package com.linrun.trigger.http;

import com.linrun.api.knowledgeasset.request.BackupKnowledgeVectorRequest;
import com.linrun.api.knowledgeasset.request.EvaluateKnowledgeRecallRequest;
import com.linrun.api.knowledgeasset.request.RebuildKnowledgeVectorRequest;
import com.linrun.api.knowledgeasset.response.KnowledgeVectorMaintenanceResponse;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.trigger.service.KnowledgeVectorOpsService;
import com.linrun.types.response.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/knowledge/vector")
public class KnowledgeVectorController {

    private final KnowledgeVectorOpsService knowledgeVectorOpsService;

    public KnowledgeVectorController(KnowledgeVectorOpsService knowledgeVectorOpsService) {
        this.knowledgeVectorOpsService = knowledgeVectorOpsService;
    }

    @PostMapping("/rebuild")
    public Response<KnowledgeVectorMaintenanceResponse> rebuild(@RequestBody RebuildKnowledgeVectorRequest request) {
        return Response.success(knowledgeVectorOpsService.rebuild(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/backup")
    public Response<KnowledgeVectorMaintenanceResponse> backup(@RequestBody BackupKnowledgeVectorRequest request) {
        return Response.success(knowledgeVectorOpsService.backup(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/recall-evaluate")
    public Response<KnowledgeVectorMaintenanceResponse> evaluateRecall(@RequestBody EvaluateKnowledgeRecallRequest request) {
        return Response.success(knowledgeVectorOpsService.evaluateRecall(request), RequestTraceContext.getRequestId());
    }

    @PostMapping("/compensate-failed-embedding")
    public Response<KnowledgeVectorMaintenanceResponse> compensateFailedEmbedding(@RequestParam(defaultValue = "20") int limit) {
        return Response.success(knowledgeVectorOpsService.compensateFailedEmbedding(limit), RequestTraceContext.getRequestId());
    }
}
