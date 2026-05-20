package com.linrun.domain.knowledgeasset.service;

import com.linrun.domain.knowledgeasset.adapter.KnowledgeEmbeddingClient;
import com.linrun.domain.knowledgeasset.adapter.KnowledgeVectorRepository;
import com.linrun.domain.knowledgeasset.model.KnowledgeFragment;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeVectorServiceTest {

    @Test
    void shouldSaveFragmentEmbedding() {
        FakeKnowledgeVectorRepository vectorRepository = new FakeKnowledgeVectorRepository();
        KnowledgeVectorService service = new KnowledgeVectorService(new FakeKnowledgeEmbeddingClient(), vectorRepository);
        KnowledgeFragment fragment = fragment("KF10001", "拼团退款规则");

        service.saveFragmentEmbedding(fragment);

        assertEquals("KF10001", vectorRepository.savedFragments.get(0).getFragmentId());
        assertEquals(List.of(1.0d, 0.0d), vectorRepository.savedEmbeddings.get(0));
    }

    @Test
    void shouldSearchSimilarFragments() {
        FakeKnowledgeVectorRepository vectorRepository = new FakeKnowledgeVectorRepository();
        KnowledgeVectorService service = new KnowledgeVectorService(new FakeKnowledgeEmbeddingClient(), vectorRepository);
        vectorRepository.savedFragments.add(fragment("KF10002", "售后规则"));

        List<KnowledgeFragment> result = service.searchSimilar("售后", 3);

        assertEquals(1, result.size());
        assertEquals("KF10002", result.get(0).getFragmentId());
    }

    @Test
    void shouldRejectBlankQuestion() {
        KnowledgeVectorService service = new KnowledgeVectorService(
                new FakeKnowledgeEmbeddingClient(),
                new FakeKnowledgeVectorRepository());

        AppException exception = assertThrows(AppException.class, () -> service.searchSimilar(" ", 3));

        assertEquals("0001", exception.getCode());
        assertEquals("question cannot be blank", exception.getMessage());
    }

    private KnowledgeFragment fragment(String fragmentId, String content) {
        KnowledgeFragment fragment = new KnowledgeFragment();
        fragment.setFragmentId(fragmentId);
        fragment.setContent(content);
        return fragment;
    }

    private static class FakeKnowledgeEmbeddingClient implements KnowledgeEmbeddingClient {

        @Override
        public List<Double> embed(String content) {
            return List.of(1.0d, 0.0d);
        }
    }

    private static class FakeKnowledgeVectorRepository implements KnowledgeVectorRepository {

        private final List<KnowledgeFragment> savedFragments = new ArrayList<>();
        private final List<List<Double>> savedEmbeddings = new ArrayList<>();

        @Override
        public void saveEmbedding(KnowledgeFragment fragment, List<Double> embedding) {
            savedFragments.add(fragment);
            savedEmbeddings.add(embedding);
        }

        @Override
        public List<KnowledgeFragment> searchSimilar(List<Double> queryEmbedding, int limit) {
            return savedFragments.stream()
                    .limit(limit)
                    .toList();
        }
    }
}
