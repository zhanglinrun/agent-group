package com.linrun.domain.guide.model;

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
    private List<String> usageScenarios = new ArrayList<>();

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

    public List<String> getUsageScenarios() {
        return usageScenarios;
    }

    public void setUsageScenarios(List<String> usageScenarios) {
        this.usageScenarios = usageScenarios;
    }
}
