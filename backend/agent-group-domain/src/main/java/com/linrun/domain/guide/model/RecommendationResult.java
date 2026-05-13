package com.linrun.domain.guide.model;

import java.util.ArrayList;
import java.util.List;

public class RecommendationResult {

    private GuideProduct primaryProduct;
    private List<GuideProduct> candidates = new ArrayList<>();
    private List<RecommendationReason> reasons = new ArrayList<>();
    private boolean passedSelfCheck;
    private String selfCheckMessage;

    public GuideProduct getPrimaryProduct() {
        if (primaryProduct != null) {
            return primaryProduct;
        }
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public void setPrimaryProduct(GuideProduct primaryProduct) {
        this.primaryProduct = primaryProduct;
    }

    public List<GuideProduct> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<GuideProduct> candidates) {
        this.candidates = candidates == null ? new ArrayList<>() : new ArrayList<>(candidates);
    }

    public List<RecommendationReason> getReasons() {
        return reasons;
    }

    public void setReasons(List<RecommendationReason> reasons) {
        this.reasons = reasons == null ? new ArrayList<>() : new ArrayList<>(reasons);
    }

    public boolean isPassedSelfCheck() {
        return passedSelfCheck;
    }

    public void setPassedSelfCheck(boolean passedSelfCheck) {
        this.passedSelfCheck = passedSelfCheck;
    }

    public String getSelfCheckMessage() {
        return selfCheckMessage;
    }

    public void setSelfCheckMessage(String selfCheckMessage) {
        this.selfCheckMessage = selfCheckMessage;
    }

    public void addCandidate(GuideProduct product) {
        if (product != null) {
            candidates.add(product);
        }
    }

    public void addReason(String reasonType, String content, int weight) {
        reasons.add(new RecommendationReason(reasonType, content, weight));
    }
}
