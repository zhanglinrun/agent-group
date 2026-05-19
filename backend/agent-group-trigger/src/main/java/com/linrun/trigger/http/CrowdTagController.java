package com.linrun.trigger.http;

import com.linrun.api.tag.request.ExecuteCrowdTagJobRequest;
import com.linrun.api.tag.response.CrowdTagJobResponse;
import com.linrun.domain.tag.model.CrowdTagJobResult;
import com.linrun.domain.tag.service.CrowdTagService;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.response.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
