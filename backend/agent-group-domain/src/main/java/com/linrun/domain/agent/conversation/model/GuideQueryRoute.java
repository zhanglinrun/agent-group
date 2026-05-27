package com.linrun.domain.agent.conversation.model;

import java.util.List;

public class GuideQueryRoute {

    private String strategy;
    private String intent;
    private String reason;
    private double confidence;
    private List<String> retrievers;

    public static GuideQueryRoute of(String strategy,
                                     String intent,
                                     String reason,
                                     double confidence,
                                     List<String> retrievers) {
        GuideQueryRoute route = new GuideQueryRoute();
        route.setStrategy(strategy);
        route.setIntent(intent);
        route.setReason(reason);
        route.setConfidence(confidence);
        route.setRetrievers(retrievers == null ? List.of() : retrievers);
        return route;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public List<String> getRetrievers() {
        return retrievers;
    }

    public void setRetrievers(List<String> retrievers) {
        this.retrievers = retrievers;
    }
}
