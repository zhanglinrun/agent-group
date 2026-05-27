package com.linrun.trigger.http;

import com.linrun.api.dto.GuideEvaluationFeedbackDTO;
import com.linrun.api.dto.GuideEvaluationItemDTO;
import com.linrun.api.dto.GuideEvaluationReportResponse;
import com.linrun.domain.agent.quality.model.GuideEvaluationFeedback;
import com.linrun.domain.agent.quality.model.GuideEvaluationItemResult;
import com.linrun.domain.agent.quality.model.GuideEvaluationReport;
import com.linrun.domain.agent.quality.service.GuideEvaluationService;
import org.springframework.stereotype.Service;

@Service
public class GuideEvaluationTriggerHandler {

    private final GuideEvaluationService guideEvaluationService;

    public GuideEvaluationTriggerHandler(GuideEvaluationService guideEvaluationService) {
        this.guideEvaluationService = guideEvaluationService;
    }

    public GuideEvaluationReportResponse runGuideEvaluation() {
        return response(guideEvaluationService.runBatch());
    }

    public GuideEvaluationReportResponse queryLatestReport() {
        return response(guideEvaluationService.queryLatestReport());
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
        response.setToolCallAccuracyRate(report.getToolCallAccuracyRate());
        response.setToolArgumentAccuracyRate(report.getToolArgumentAccuracyRate());
        response.setToolResultReferenceRate(report.getToolResultReferenceRate());
        response.setAverageLatencyMillis(report.getAverageLatencyMillis());
        response.setP99LatencyMillis(report.getP99LatencyMillis());
        response.setTotalPromptTokens(report.getTotalPromptTokens());
        response.setTotalCompletionTokens(report.getTotalCompletionTokens());
        response.setTotalTokens(report.getTotalTokens());
        response.setEstimatedCostYuan(report.getEstimatedCostYuan());
        response.setBaselineBatchNo(report.getBaselineBatchNo());
        response.setRetrievalHitRateDelta(report.getRetrievalHitRateDelta());
        response.setAnswerAccuracyRateDelta(report.getAnswerAccuracyRateDelta());
        response.setRecommendationReasonableRateDelta(report.getRecommendationReasonableRateDelta());
        response.setContextConsistencyRateDelta(report.getContextConsistencyRateDelta());
        response.setItems(report.getItems().stream().map(this::item).toList());
        response.setFeedbacks(report.getFeedbacks().stream().map(this::feedback).toList());
        return response;
    }

    private GuideEvaluationFeedbackDTO feedback(GuideEvaluationFeedback feedback) {
        GuideEvaluationFeedbackDTO dto = new GuideEvaluationFeedbackDTO();
        dto.setTargetType(feedback.getTargetType());
        dto.setPriority(feedback.getPriority());
        dto.setContent(feedback.getContent());
        return dto;
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
        dto.setActualToolNames(itemResult.getActualToolNames());
        dto.setToolCallPassed(itemResult.isToolCallPassed());
        dto.setToolArgumentPassed(itemResult.isToolArgumentPassed());
        dto.setToolResultReferencePassed(itemResult.isToolResultReferencePassed());
        dto.setLatencyMillis(itemResult.getLatencyMillis());
        dto.setLlmLatencyMillis(itemResult.getLlmLatencyMillis());
        dto.setPromptTokens(itemResult.getPromptTokens());
        dto.setCompletionTokens(itemResult.getCompletionTokens());
        dto.setTotalTokens(itemResult.getTotalTokens());
        dto.setEstimatedCostYuan(itemResult.getEstimatedCostYuan());
        dto.setFallbackUsed(itemResult.isFallbackUsed());
        dto.setScore(itemResult.getScore());
        dto.setSuggestion(itemResult.getSuggestion());
        return dto;
    }
}
