package com.linrun.domain.knowledgeasset.adapter;

import java.util.List;

public interface KnowledgeEmbeddingClient {

    List<Double> embed(String content);
}
