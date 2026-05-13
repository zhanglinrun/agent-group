package com.linrun.domain.knowledge.adapter;

import java.util.List;

public interface KnowledgeEmbeddingClient {

    List<Double> embed(String content);
}
