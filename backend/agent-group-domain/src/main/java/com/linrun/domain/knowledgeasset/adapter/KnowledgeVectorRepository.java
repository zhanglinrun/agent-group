package com.linrun.domain.knowledgeasset.adapter;

import com.linrun.domain.knowledgeasset.model.KnowledgeFragment;

import java.util.List;

public interface KnowledgeVectorRepository {

    void saveFragment(KnowledgeFragment fragment);

    List<KnowledgeFragment> searchSimilar(String question, int limit);
}
