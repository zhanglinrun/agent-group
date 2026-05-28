package com.linrun.domain.agent.conversation.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class GuideIntent {

    private GuideIntentType intentType;
    private boolean budgetSensitive;
    private boolean groupBuyConcerned;
    private boolean afterSaleConcerned;
    private boolean compareConcerned;
    private boolean performanceSensitive;
    private boolean portabilitySensitive;
    private BigDecimal budgetUpperLimit;
    private String userIdentity;
    private String orderId;
    private String goodsId;
    private String normalizedQuestion;
    private List<String> usageScenarios = new ArrayList<>();
    private List<String> entities = new ArrayList<>();

    public GuideIntentType getIntentType() {
        return intentType;
    }

    public void setIntentType(GuideIntentType intentType) {
        this.intentType = intentType;
    }

    public boolean isBudgetSensitive() {
        return budgetSensitive;
    }

    public void setBudgetSensitive(boolean budgetSensitive) {
        this.budgetSensitive = budgetSensitive;
    }

    public boolean isGroupBuyConcerned() {
        return groupBuyConcerned;
    }

    public void setGroupBuyConcerned(boolean groupBuyConcerned) {
        this.groupBuyConcerned = groupBuyConcerned;
    }

    public boolean isAfterSaleConcerned() {
        return afterSaleConcerned;
    }

    public void setAfterSaleConcerned(boolean afterSaleConcerned) {
        this.afterSaleConcerned = afterSaleConcerned;
    }

    public boolean isCompareConcerned() {
        return compareConcerned;
    }

    public void setCompareConcerned(boolean compareConcerned) {
        this.compareConcerned = compareConcerned;
    }

    public boolean isPerformanceSensitive() {
        return performanceSensitive;
    }

    public void setPerformanceSensitive(boolean performanceSensitive) {
        this.performanceSensitive = performanceSensitive;
    }

    public boolean isPortabilitySensitive() {
        return portabilitySensitive;
    }

    public void setPortabilitySensitive(boolean portabilitySensitive) {
        this.portabilitySensitive = portabilitySensitive;
    }

    public BigDecimal getBudgetUpperLimit() {
        return budgetUpperLimit;
    }

    public void setBudgetUpperLimit(BigDecimal budgetUpperLimit) {
        this.budgetUpperLimit = budgetUpperLimit;
    }

    public String getUserIdentity() {
        return userIdentity;
    }

    public void setUserIdentity(String userIdentity) {
        this.userIdentity = userIdentity;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getGoodsId() {
        return goodsId;
    }

    public void setGoodsId(String goodsId) {
        this.goodsId = goodsId;
    }

    public String getNormalizedQuestion() {
        return normalizedQuestion;
    }

    public void setNormalizedQuestion(String normalizedQuestion) {
        this.normalizedQuestion = normalizedQuestion;
    }

    public List<String> getUsageScenarios() {
        return usageScenarios;
    }

    public void setUsageScenarios(List<String> usageScenarios) {
        this.usageScenarios = usageScenarios;
    }

    public List<String> getEntities() {
        return entities;
    }

    public void setEntities(List<String> entities) {
        this.entities = entities;
    }
}
