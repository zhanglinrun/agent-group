package com.linrun.domain.agent.knowledge.model;

import java.util.List;

public class KnowledgeDocumentBuildResult {

    private KnowledgeDocument document;
    private List<KnowledgeFragment> fragments;

    public KnowledgeDocumentBuildResult(KnowledgeDocument document, List<KnowledgeFragment> fragments) {
        this.document = document;
        this.fragments = fragments;
    }

    public KnowledgeDocument getDocument() {
        return document;
    }

    public void setDocument(KnowledgeDocument document) {
        this.document = document;
    }

    public List<KnowledgeFragment> getFragments() {
        return fragments;
    }

    public void setFragments(List<KnowledgeFragment> fragments) {
        this.fragments = fragments;
    }
}















