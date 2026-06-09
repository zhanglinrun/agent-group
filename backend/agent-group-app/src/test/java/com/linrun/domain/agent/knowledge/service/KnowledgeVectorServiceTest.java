package com.linrun.domain.agent.knowledge.service;

import com.linrun.domain.agent.knowledge.adapter.KnowledgeVectorRepository;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
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
        KnowledgeVectorService service = new KnowledgeVectorService(vectorRepository);
        KnowledgeFragment fragment = fragment("KF10001", "拼团退款规�?);

        service.saveFragmentEmbedding(fragment);

        assertEquals("KF10001", vectorRepository.savedFragments.get(0).getFragmentId());
    }

    @Test
    void shouldSkipParentFragmentEmbedding() {
        FakeKnowledgeVectorRepository vectorRepository = new FakeKnowledgeVectorRepository();
        KnowledgeVectorService service = new KnowledgeVectorService(vectorRepository);
        KnowledgeFragment fragment = fragment("KF10000", "parent full context");
        fragment.setEmbeddingEnabled(false);

        service.saveFragmentEmbedding(fragment);

        assertEquals(0, vectorRepository.savedFragments.size());
    }

    @Test
    void shouldSearchSimilarFragments() {
        FakeKnowledgeVectorRepository vectorRepository = new FakeKnowledgeVectorRepository();
        KnowledgeVectorService service = new KnowledgeVectorService(vectorRepository);
        vectorRepository.savedFragments.add(fragment("KF10002", "售后规则"));

        List<KnowledgeFragment> result = service.searchSimilar("售后", 3);

        assertEquals(1, result.size());
        assertEquals("KF10002", result.get(0).getFragmentId());
    }

    @Test
    void shouldRejectBlankQuestion() {
        KnowledgeVectorService service = new KnowledgeVectorService(new FakeKnowledgeVectorRepository());

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

    private static class FakeKnowledgeVectorRepository implements KnowledgeVectorRepository {

        private final List<KnowledgeFragment> savedFragments = new ArrayList<>();

        @Override
        public void saveFragment(KnowledgeFragment fragment) {
            savedFragments.add(fragment);
        }

        @Override
        public List<KnowledgeFragment> searchSimilar(String question, int limit) {
            return savedFragments.stream()
                    .limit(limit)
                    .toList();
        }
    }
}















