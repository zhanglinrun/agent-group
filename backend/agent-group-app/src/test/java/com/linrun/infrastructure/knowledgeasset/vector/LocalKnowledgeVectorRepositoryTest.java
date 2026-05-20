package com.linrun.infrastructure.knowledgeasset.vector;

import com.linrun.domain.knowledgeasset.model.KnowledgeFragment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalKnowledgeVectorRepositoryTest {

    @Test
    void shouldSearchByCosineSimilarity() {
        LocalKnowledgeEmbeddingClient embeddingClient = new LocalKnowledgeEmbeddingClient(32);
        LocalKnowledgeVectorRepository repository = new LocalKnowledgeVectorRepository();
        KnowledgeFragment refund = fragment("KF10001", "拼团失败会自动退款");
        KnowledgeFragment spec = fragment("KF10002", "标准版适合写论文");
        repository.saveEmbedding(refund, embeddingClient.embed(refund.getContent()));
        repository.saveEmbedding(spec, embeddingClient.embed(spec.getContent()));

        List<KnowledgeFragment> result = repository.searchSimilar(embeddingClient.embed("退款"), 1);

        assertEquals(1, result.size());
        assertEquals("KF10001", result.get(0).getFragmentId());
    }

    @Test
    void shouldBuildNormalizedEmbedding() {
        LocalKnowledgeEmbeddingClient embeddingClient = new LocalKnowledgeEmbeddingClient(32);

        List<Double> embedding = embeddingClient.embed("售后");

        double length = Math.sqrt(embedding.stream()
                .mapToDouble(value -> value * value)
                .sum());
        assertTrue(Math.abs(length - 1.0d) < 0.000001d);
    }

    private KnowledgeFragment fragment(String fragmentId, String content) {
        KnowledgeFragment fragment = new KnowledgeFragment();
        fragment.setFragmentId(fragmentId);
        fragment.setContent(content);
        return fragment;
    }
}
