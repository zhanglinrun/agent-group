package com.linrun.domain.agent.file.model;

import java.util.Map;

/**
 * RAG 语义检索单条命中结果。
 */
public record RagHit(
        int rank,
        String documentId,
        String content,
        Map<String, Object> metadata) {
}
