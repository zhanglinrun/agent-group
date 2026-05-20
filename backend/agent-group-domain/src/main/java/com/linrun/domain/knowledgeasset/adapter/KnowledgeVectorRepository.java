package com.linrun.domain.knowledgeasset.adapter;

import com.linrun.domain.knowledgeasset.model.KnowledgeFragment;

import java.util.List;

public interface KnowledgeVectorRepository {

    void saveEmbedding(KnowledgeFragment fragment, List<Double> embedding);

    List<KnowledgeFragment> searchSimilar(List<Double> queryEmbedding, int limit);
}
