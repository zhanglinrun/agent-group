package com.linrun.domain.agent.quality.model;

import com.linrun.domain.agent.conversation.model.GuideIntentType;

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
    private List<String> forbiddenAnswerKeywords = new ArrayList<>();
    private List<String> expectedToolNames = new ArrayList<>();
    private List<String> expectedToolOrder = new ArrayList<>();
    private List<String> scenarioTags = new ArrayList<>();

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
        this.requiredReferenceKeywords = requiredReferenceKeywords == null ? new ArrayList<>() : new ArrayList<>(requiredReferenceKeywords);
    }

    public List<String> getRequiredAnswerKeywords() {
        return requiredAnswerKeywords;
    }

    public void setRequiredAnswerKeywords(List<String> requiredAnswerKeywords) {
        this.requiredAnswerKeywords = requiredAnswerKeywords == null ? new ArrayList<>() : new ArrayList<>(requiredAnswerKeywords);
    }

    public List<String> getForbiddenAnswerKeywords() {
        return forbiddenAnswerKeywords;
    }

    public void setForbiddenAnswerKeywords(List<String> forbiddenAnswerKeywords) {
        this.forbiddenAnswerKeywords = forbiddenAnswerKeywords == null ? new ArrayList<>() : new ArrayList<>(forbiddenAnswerKeywords);
    }

    public List<String> getExpectedToolNames() {
        return expectedToolNames;
    }

    public void setExpectedToolNames(List<String> expectedToolNames) {
        this.expectedToolNames = expectedToolNames == null ? new ArrayList<>() : new ArrayList<>(expectedToolNames);
    }

    public List<String> getExpectedToolOrder() {
        return expectedToolOrder;
    }

    public void setExpectedToolOrder(List<String> expectedToolOrder) {
        this.expectedToolOrder = expectedToolOrder == null ? new ArrayList<>() : new ArrayList<>(expectedToolOrder);
    }

    public List<String> getScenarioTags() {
        return scenarioTags;
    }

    public void setScenarioTags(List<String> scenarioTags) {
        this.scenarioTags = scenarioTags == null ? new ArrayList<>() : new ArrayList<>(scenarioTags);
    }
}
