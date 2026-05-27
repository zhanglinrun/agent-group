package com.linrun.trigger.service;

import com.linrun.api.knowledgeasset.request.BackupKnowledgeVectorRequest;
import com.linrun.api.knowledgeasset.request.EvaluateKnowledgeRecallRequest;
import com.linrun.api.knowledgeasset.request.RebuildKnowledgeVectorRequest;
import com.linrun.api.knowledgeasset.response.KnowledgeVectorMaintenanceResponse;
import com.linrun.domain.knowledgeasset.model.KnowledgeVectorMaintenanceReport;
import com.linrun.domain.knowledgeasset.service.KnowledgeVectorMaintenanceService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeVectorOpsService {

    private final KnowledgeVectorMaintenanceService knowledgeVectorMaintenanceService;

    public KnowledgeVectorOpsService(KnowledgeVectorMaintenanceService knowledgeVectorMaintenanceService) {
        this.knowledgeVectorMaintenanceService = knowledgeVectorMaintenanceService;
    }

    public KnowledgeVectorMaintenanceResponse rebuild(RebuildKnowledgeVectorRequest request) {
        if (request == null) {
            throw new AppException("0001", "重建参数不能为空");
        }
        return toResponse(knowledgeVectorMaintenanceService.rebuildVersion(request.getKnowledgeVersion()));
    }

    public KnowledgeVectorMaintenanceResponse backup(BackupKnowledgeVectorRequest request) {
        if (request == null) {
            throw new AppException("0001", "备份参数不能为空");
        }
        return toResponse(knowledgeVectorMaintenanceService.backupVersion(request.getKnowledgeVersion()));
    }

    public KnowledgeVectorMaintenanceResponse evaluateRecall(EvaluateKnowledgeRecallRequest request) {
        if (request == null) {
            throw new AppException("0001", "召回评测参数不能为空");
        }
        int topK = request.getTopK() == null ? 3 : request.getTopK();
        return toResponse(knowledgeVectorMaintenanceService.evaluateRecall(
                request.getQuestion(),
                request.getExpectedFragmentIds(),
                topK));
    }

    public KnowledgeVectorMaintenanceResponse compensateFailedEmbedding(int limit) {
        return toResponse(knowledgeVectorMaintenanceService.compensateFailedEmbedding(limit));
    }

    private KnowledgeVectorMaintenanceResponse toResponse(KnowledgeVectorMaintenanceReport report) {
        KnowledgeVectorMaintenanceResponse response = new KnowledgeVectorMaintenanceResponse();
        response.setAction(report.getAction());
        response.setKnowledgeVersion(report.getKnowledgeVersion());
        response.setFragmentCount(report.getFragmentCount());
        response.setSuccessCount(report.getSuccessCount());
        response.setFailedCount(report.getFailedCount());
        response.setExpectedCount(report.getExpectedCount());
        response.setMatchedCount(report.getMatchedCount());
        response.setRecallHitRate(report.getRecallHitRate());
        response.setSnapshotId(report.getSnapshotId());
        response.setHitFragmentIds(report.getHitFragmentIds());
        response.setMessage(report.getMessage());
        return response;
    }
}
