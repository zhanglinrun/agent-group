package com.linrun.domain.quality.model;

import com.linrun.domain.conversation.model.GuideIntentType;

import java.util.ArrayList;
import java.util.List;

public class GuideEvaluationCase {

    private String caseId;
    private String caseName;
    private String question;
    private GuideIntentType expectedIntentType;
    private String expectedGoodsId;
    private boolean contextRequired;
    private List<String> requiredReferenceKeywords = new ArrayList<>();
    private List<String> requiredAnswerKeywords = new ArrayList<>();
    private List<String> expectedToolNames = new ArrayList<>();

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getCaseName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public GuideIntentType getExpectedIntentType() {
        return expectedIntentType;
    }

    public void setExpectedIntentType(GuideIntentType expectedIntentType) {
        this.expectedIntentType = expectedIntentType;
    }

    public String getExpectedGoodsId() {
        return expectedGoodsId;
    }

    public void setExpectedGoodsId(String expectedGoodsId) {
        this.expectedGoodsId = expectedGoodsId;
    }

    public boolean isContextRequired() {
        return contextRequired;
    }

    public void setContextRequired(boolean contextRequired) {
        this.contextRequired = contextRequired;
    }

    public List<String> getRequiredReferenceKeywords() {
        return requiredReferenceKeywords;
    }

    public void setRequiredReferenceKeywords(List<String> requiredReferenceKeywords) {
        this.requiredReferenceKeywords = requiredReferenceKeywords;
    }

    public List<String> getRequiredAnswerKeywords() {
        return requiredAnswerKeywords;
    }

    public void setRequiredAnswerKeywords(List<String> requiredAnswerKeywords) {
        this.requiredAnswerKeywords = requiredAnswerKeywords;
    }

    public List<String> getExpectedToolNames() {
        return expectedToolNames;
    }

    public void setExpectedToolNames(List<String> expectedToolNames) {
        this.expectedToolNames = expectedToolNames == null ? new ArrayList<>() : new ArrayList<>(expectedToolNames);
    }
}
