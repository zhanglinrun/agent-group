package com.linrun.trigger.http.agent;

import com.linrun.api.dto.KnowledgeDocumentDTO;
import com.linrun.api.dto.KnowledgeDocumentFullContentResponse;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeDocumentRepository;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocument;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocumentStatus;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragmentStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeDocumentAdminHandlerTest {

    @Test
    void shouldReturnFullContentByFragmentRank() {
        FakeKnowledgeDocumentRepository repository = new FakeKnowledgeDocumentRepository();
        KnowledgeDocumentAdminHandler handler = new KnowledgeDocumentAdminHandler(repository);

        KnowledgeDocumentFullContentResponse response = handler.queryFullContent("DOC10001");

        assertEquals("DOC10001", response.getDocumentId());
        assertEquals(2, response.getFragmentCount());
        assertEquals("first paragraph\n\nsecond paragraph", response.getContent());
        assertEquals(1, response.getFragments().get(0).getRankNo());
        assertEquals("PARSED", response.getParseStatus());
        assertEquals("READY", response.getEmbeddingStatus());
        assertEquals(Boolean.TRUE, response.getRetrievalReady());
        assertEquals("DOC10001#1", response.getFragments().get(0).getCitationLabel());
        assertEquals("first paragraph", response.getCitationSnippets().get(0));
    }

    @Test
    void shouldExposeEmbeddingFailureReason() {
        FakeKnowledgeDocumentRepository repository = new FakeKnowledgeDocumentRepository();
        repository.document.setDocumentStatus(KnowledgeDocumentStatus.EMBEDDING_FAILED);
        repository.document.setEnabled(false);
        KnowledgeDocumentAdminHandler handler = new KnowledgeDocumentAdminHandler(repository);

        KnowledgeDocumentDTO response = handler.queryDocuments(null, 10).getFirst();

        assertEquals("PARSED", response.getParseStatus());
        assertEquals("FAILED", response.getEmbeddingStatus());
        assertEquals(Boolean.FALSE, response.getRetrievalReady());
        assertTrue(response.getFailureReason().contains("向量入库失败"));
    }

    @Test
    void shouldDisableDocumentAndFragments() {
        FakeKnowledgeDocumentRepository repository = new FakeKnowledgeDocumentRepository();
        KnowledgeDocumentAdminHandler handler = new KnowledgeDocumentAdminHandler(repository);

        KnowledgeDocumentDTO response = handler.disableDocument("DOC10001");

        assertEquals(KnowledgeDocumentStatus.DISABLED.name(), response.getDocumentStatus());
        assertFalse(response.getEnabled());
        assertEquals("DISABLED", response.getEmbeddingStatus());
        assertEquals(Boolean.FALSE, response.getRetrievalReady());
        assertEquals("文档已停用，不参与检索", response.getFailureReason());
        assertEquals(KnowledgeFragmentStatus.DISABLED, repository.fragments.get(0).getFragmentStatus());
        assertFalse(repository.fragments.get(0).getEnabled());
    }

    private static class FakeKnowledgeDocumentRepository implements KnowledgeDocumentRepository {

        private final KnowledgeDocument document = document();
        private final List<KnowledgeFragment> fragments = new ArrayList<>(List.of(
                fragment("KF10002", 2, "second paragraph"),
                fragment("KF10001", 1, "first paragraph")));

        @Override
        public void save(KnowledgeDocument document, List<KnowledgeFragment> fragments) {
        }

        @Override
        public Optional<KnowledgeDocument> queryDocumentByDocumentId(String documentId) {
            return document.getDocumentId().equals(documentId) ? Optional.of(document) : Optional.empty();
        }

        @Override
        public List<KnowledgeFragment> queryFragmentsByDocumentId(String documentId) {
            return fragments.stream()
                    .filter(fragment -> fragment.getDocumentId().equals(documentId))
                    .toList();
        }

        @Override
        public List<KnowledgeFragment> queryEnabledFragmentsByVersion(String knowledgeVersion) {
            return fragments.stream()
                    .filter(fragment -> fragment.getKnowledgeVersion().equals(knowledgeVersion)
                            && Boolean.TRUE.equals(fragment.getEnabled()))
                    .toList();
        }

        @Override
        public List<KnowledgeDocument> queryDocumentsByStatus(KnowledgeDocumentStatus status, int limit) {
            if (status != null && !status.equals(document.getDocumentStatus())) {
                return List.of();
            }
            return List.of(document);
        }

        @Override
        public void updateDocumentStatus(KnowledgeDocument document) {
            this.document.setDocumentStatus(document.getDocumentStatus());
            this.document.setEnabled(document.getEnabled());
            this.document.setUpdateTime(document.getUpdateTime());
        }

        @Override
        public void updateFragmentsStatusByDocumentId(String documentId,
                                                      KnowledgeFragmentStatus status,
                                                      boolean enabled) {
            fragments.stream()
                    .filter(fragment -> fragment.getDocumentId().equals(documentId))
                    .forEach(fragment -> {
                        fragment.setFragmentStatus(status);
                        fragment.setEnabled(enabled);
                    });
        }

        private static KnowledgeDocument document() {
            KnowledgeDocument document = new KnowledgeDocument();
            document.setDocumentId("DOC10001");
            document.setDocumentName("Knowledge Manual");
            document.setDocumentType("Rule");
            document.setKnowledgeVersion("v1");
            document.setSourceType("OPERATOR_UPLOAD");
            document.setSourceName("manual.md");
            document.setDocumentStatus(KnowledgeDocumentStatus.ENABLED);
            document.setEnabled(true);
            document.setCreateTime(LocalDateTime.now());
            document.setUpdateTime(LocalDateTime.now());
            return document;
        }

        private static KnowledgeFragment fragment(String fragmentId, int rankNo, String content) {
            KnowledgeFragment fragment = new KnowledgeFragment();
            fragment.setFragmentId(fragmentId);
            fragment.setDocumentId("DOC10001");
            fragment.setGoodsId("global");
            fragment.setDocumentType("Rule");
            fragment.setKnowledgeVersion("v1");
            fragment.setContent(content);
            fragment.setRankNo(rankNo);
            fragment.setChunkType("CHILD");
            fragment.setEmbeddingEnabled(true);
            fragment.setFragmentStatus(KnowledgeFragmentStatus.ENABLED);
            fragment.setEnabled(true);
            fragment.setCreateTime(LocalDateTime.now());
            fragment.setUpdateTime(LocalDateTime.now());
            return fragment;
        }
    }
}













