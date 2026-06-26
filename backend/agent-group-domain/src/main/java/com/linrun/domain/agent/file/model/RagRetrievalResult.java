package com.linrun.domain.agent.file.model;

import java.util.List;

/**
 * RAG 语义检索结果。
 */
public record RagRetrievalResult(
        boolean success,
        String mode,
        String originalQuestion,
        String compressedQuestion,
        List<String> expandedQueries,
        int hitCount,
        String message,
        List<RagHit> hits) {

    public static RagRetrievalResult failed(String mode,
                                            String originalQuestion,
                                            String compressedQuestion,
                                            List<String> expandedQueries,
                                            String message) {
        return new RagRetrievalResult(false, mode, originalQuestion, compressedQuestion,
                expandedQueries == null ? List.of() : expandedQueries, 0,
                (message == null || message.isBlank()) ? "检索失败" : message, List.of());
    }
}
