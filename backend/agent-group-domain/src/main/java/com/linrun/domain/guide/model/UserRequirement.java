package com.linrun.domain.guide.model;

import java.util.ArrayList;
import java.util.List;

public class UserRequirement {

    private String userIdentity;
    private List<String> usageScenarios = new ArrayList<>();
    private boolean budgetSensitive;
    private boolean groupBuyConcerned;
    private boolean afterSaleConcerned;
    private boolean compareConcerned;

    public static UserRequirement fromIntent(GuideIntent intent) {
        UserRequirement requirement = new UserRequirement();
        requirement.setUserIdentity(intent.getUserIdentity());
        requirement.setUsageScenarios(intent.getUsageScenarios());
        requirement.setBudgetSensitive(intent.isBudgetSensitive());
        requirement.setGroupBuyConcerned(intent.isGroupBuyConcerned());
        requirement.setAfterSaleConcerned(intent.isAfterSaleConcerned());
        requirement.setCompareConcerned(intent.isCompareConcerned());
        return requirement;
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
        this.usageScenarios = usageScenarios == null ? new ArrayList<>() : new ArrayList<>(usageScenarios);
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
}
