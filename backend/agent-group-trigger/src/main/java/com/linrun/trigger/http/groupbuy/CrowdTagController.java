package com.linrun.trigger.http.groupbuy;

import com.linrun.api.dto.ExecuteCrowdTagJobRequest;
import com.linrun.api.dto.CrowdTagJobResponse;
import com.linrun.domain.groupbuy.tag.model.CrowdTagJobResult;
import com.linrun.domain.groupbuy.tag.service.CrowdTagService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/gbm/tag")
public class CrowdTagController {

    private final CrowdTagService crowdTagService;

    public CrowdTagController(CrowdTagService crowdTagService) {
        this.crowdTagService = crowdTagService;
    }

    @PostMapping("/exec_tag_batch_job")
    public Response<CrowdTagJobResponse> execTagBatchJob(@RequestBody ExecuteCrowdTagJobRequest request) {
        CrowdTagJobResult result = crowdTagService.execTagBatchJob(request.getTagId(), request.getBatchId());
        return Response.success(toResponse(result), RequestTraceContext.getRequestId());
    }

    @PostMapping("/exec_pending_tag_jobs")
    public Response<List<CrowdTagJobResponse>> execPendingTagJobs(@RequestParam(defaultValue = "20") int limit) {
        List<CrowdTagJobResponse> responses = crowdTagService.execRunnableTagBatchJobs(limit).stream()
                .map(this::toResponse)
                .toList();
        return Response.success(responses, RequestTraceContext.getRequestId());
    }

    @PostMapping("/refresh_statistics")
    public Response<CrowdTagJobResponse> refreshStatistics(@RequestParam String tagId) {
        return Response.success(toResponse(crowdTagService.refreshCrowdTagStatistics(tagId)),
                RequestTraceContext.getRequestId());
    }

    private CrowdTagJobResponse toResponse(CrowdTagJobResult result) {
        CrowdTagJobResponse response = new CrowdTagJobResponse();
        response.setTagId(result.getTagId());
        response.setBatchId(result.getBatchId());
        response.setTagType(result.getTagType());
        response.setTagRule(result.getTagRule());
        response.setMatchedCount(result.getMatchedCount());
        response.setUserIds(result.getUserIds());
        response.setMessage(result.getMessage());
        return response;
    }
}















