package com.linrun.domain.knowledge.adapter;

import com.linrun.domain.knowledge.model.KnowledgeFragment;

import java.util.List;

public interface KnowledgeVectorRepository {

    void saveEmbedding(KnowledgeFragment fragment, List<Double> embedding);

    List<KnowledgeFragment> searchSimilar(List<Double> queryEmbedding, int limit);
}
