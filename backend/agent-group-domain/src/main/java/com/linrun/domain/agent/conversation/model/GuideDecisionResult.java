package com.linrun.domain.agent.conversation.model;

import java.util.ArrayList;
import java.util.List;

public class GuideDecisionResult {

    private GuideIntent intent;
    private UserRequirement userRequirement;
    private RecommendationResult recommendationResult;
    private GuideProduct product;
    private List<GuideReference> references = new ArrayList<>();
    private List<String> answerSegments = new ArrayList<>();

    public GuideIntent getIntent() {
        return intent;
    }

    public void setIntent(GuideIntent intent) {
        this.intent = intent;
    }

    public UserRequirement getUserRequirement() {
        return userRequirement;
    }

    public void setUserRequirement(UserRequirement userRequirement) {
        this.userRequirement = userRequirement;
    }

    public RecommendationResult getRecommendationResult() {
        return recommendationResult;
    }

    public void setRecommendationResult(RecommendationResult recommendationResult) {
        this.recommendationResult = recommendationResult;
    }

    public GuideProduct getProduct() {
        return product;
    }

    public void setProduct(GuideProduct product) {
        this.product = product;
    }

    public List<GuideReference> getReferences() {
        return references;
    }

    public void setReferences(List<GuideReference> references) {
        this.references = references;
    }

    public List<String> getAnswerSegments() {
        return answerSegments;
    }

    public void setAnswerSegments(List<String> answerSegments) {
        this.answerSegments = answerSegments;
    }
}
