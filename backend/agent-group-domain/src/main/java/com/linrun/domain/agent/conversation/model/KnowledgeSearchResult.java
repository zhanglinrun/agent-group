package com.linrun.domain.agent.conversation.model;

import java.util.List;

public class KnowledgeSearchResult {

    private GuideQueryRoute route;
    private List<GuideReference> references;

    public KnowledgeSearchResult(GuideQueryRoute route, List<GuideReference> references) {
        this.route = route;
        this.references = references == null ? List.of() : references;
    }

    public GuideQueryRoute getRoute() {
        return route;
    }

    public void setRoute(GuideQueryRoute route) {
        this.route = route;
    }

    public List<GuideReference> getReferences() {
        return references;
    }

    public void setReferences(List<GuideReference> references) {
        this.references = references;
    }
}
