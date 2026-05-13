package com.linrun.trigger.service;

import com.linrun.api.evaluate.response.GuideEvaluationItemDTO;
import com.linrun.api.evaluate.response.GuideEvaluationReportResponse;
import com.linrun.domain.evaluate.model.GuideEvaluationItemResult;
import com.linrun.domain.evaluate.model.GuideEvaluationReport;
import com.linrun.domain.evaluate.service.GuideEvaluationService;
import org.springframework.stereotype.Service;

@Service
public class GuideEvaluationTriggerService {

    private final GuideEvaluationService guideEvaluationService;

    public GuideEvaluationTriggerService(GuideEvaluationService guideEvaluationService) {
        this.guideEvaluationService = guideEvaluationService;
    }

    public GuideEvaluationReportResponse runGuideEvaluation() {
        return response(guideEvaluationService.runBatch());
    }

    private GuideEvaluationReportResponse response(GuideEvaluationReport report) {
        GuideEvaluationReportResponse response = new GuideEvaluationReportResponse();
        response.setBatchNo(report.getBatchNo());
        response.setPromptVersion(report.getPromptVersion());
        response.setKnowledgeVersion(report.getKnowledgeVersion());
        response.setTotalCount(report.getTotalCount());
        response.setRetrievalHitRate(report.getRetrievalHitRate());
        response.setAnswerAccuracyRate(report.getAnswerAccuracyRate());
        response.setRecommendationReasonableRate(report.getRecommendationReasonableRate());
        response.setContextConsistencyRate(report.getContextConsistencyRate());
        response.setItems(report.getItems().stream().map(this::item).toList());
        return response;
    }

    private GuideEvaluationItemDTO item(GuideEvaluationItemResult itemResult) {
        GuideEvaluationItemDTO dto = new GuideEvaluationItemDTO();
        dto.setCaseId(itemResult.getCaseId());
        dto.setCaseName(itemResult.getCaseName());
        dto.setQuestion(itemResult.getQuestion());
        dto.setExpectedGoodsId(itemResult.getExpectedGoodsId());
        dto.setActualGoodsId(itemResult.getActualGoodsId());
        dto.setReferencePassed(itemResult.isReferencePassed());
        dto.setAnswerPassed(itemResult.isAnswerPassed());
        dto.setRecommendationPassed(itemResult.isRecommendationPassed());
        dto.setContextPassed(itemResult.isContextPassed());
        dto.setScore(itemResult.getScore());
        dto.setSuggestion(itemResult.getSuggestion());
        return dto;
    }
}
