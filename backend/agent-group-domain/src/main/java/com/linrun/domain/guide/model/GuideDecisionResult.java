package com.linrun.domain.guide.model;

import java.util.ArrayList;
import java.util.List;

public class GuideDecisionResult {

    private GuideIntent intent;
    private GuideProduct product;
    private List<GuideReference> references = new ArrayList<>();
    private List<String> answerSegments = new ArrayList<>();

    public GuideIntent getIntent() {
        return intent;
    }

    public void setIntent(GuideIntent intent) {
        this.intent = intent;
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
