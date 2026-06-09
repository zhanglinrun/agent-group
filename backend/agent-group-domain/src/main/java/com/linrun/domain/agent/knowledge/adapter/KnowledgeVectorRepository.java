package com.linrun.domain.agent.knowledge.adapter;

import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;

import java.util.List;

public interface KnowledgeVectorRepository {

    void saveFragment(KnowledgeFragment fragment);

    List<KnowledgeFragment> searchSimilar(String question, int limit);

    default List<KnowledgeFragment> searchSimilar(String question, String goodsId, int limit) {
        return searchSimilar(question, limit);
    }
}















