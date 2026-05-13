package com.linrun.domain.knowledge.service;

import com.linrun.domain.knowledge.adapter.KnowledgeEmbeddingClient;
import com.linrun.domain.knowledge.adapter.KnowledgeVectorRepository;
import com.linrun.domain.knowledge.model.KnowledgeFragment;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class KnowledgeVectorService {

    private final KnowledgeEmbeddingClient knowledgeEmbeddingClient;
    private final KnowledgeVectorRepository knowledgeVectorRepository;

    public KnowledgeVectorService(KnowledgeEmbeddingClient knowledgeEmbeddingClient,
                                  KnowledgeVectorRepository knowledgeVectorRepository) {
        this.knowledgeEmbeddingClient = knowledgeEmbeddingClient;
        this.knowledgeVectorRepository = knowledgeVectorRepository;
    }

    public void saveFragmentEmbedding(KnowledgeFragment fragment) {
        if (fragment == null || !StringUtils.hasText(fragment.getContent())) {
            return;
        }
        knowledgeVectorRepository.saveEmbedding(fragment, knowledgeEmbeddingClient.embed(fragment.getContent()));
    }

    public List<KnowledgeFragment> searchSimilar(String question, int limit) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "question cannot be blank");
        }
        int safeLimit = limit <= 0 ? 3 : limit;
        return knowledgeVectorRepository.searchSimilar(knowledgeEmbeddingClient.embed(question), safeLimit);
    }
}
