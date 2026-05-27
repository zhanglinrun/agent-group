package com.linrun.domain.knowledgeasset.adapter;

import com.linrun.domain.conversation.model.GuideReference;

import java.util.Comparator;
import java.util.List;

public interface KnowledgeReranker {

    List<GuideReference> rerank(String question, List<GuideReference> references, int limit);

    static KnowledgeReranker noop() {
        return (question, references, limit) -> {
            if (references == null || references.isEmpty()) {
                return List.of();
            }
            return references.stream()
                    .sorted(Comparator.comparingInt(reference ->
                            reference.getRank() == null ? Integer.MAX_VALUE : reference.getRank()))
                    .limit(Math.max(1, limit))
                    .toList();
        };
    }
}
