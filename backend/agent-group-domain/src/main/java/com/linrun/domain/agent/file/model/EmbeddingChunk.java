package com.linrun.domain.agent.file.model;

import java.util.Map;

/**
 * 向量化文本片段，infrastructure 实现时转成 Spring AI Document 入库。
 */
public record EmbeddingChunk(
        String text,
        Map<String, Object> metadata) {

    public static EmbeddingChunk of(String text, Map<String, Object> metadata) {
        return new EmbeddingChunk(text, metadata == null ? Map.of() : metadata);
    }
}
