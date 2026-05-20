package com.linrun.domain.knowledgeasset.service;

import com.linrun.domain.knowledgeasset.adapter.KnowledgeDocumentRepository;
import com.linrun.domain.knowledgeasset.adapter.KnowledgeEmbeddingClient;
import com.linrun.domain.knowledgeasset.adapter.KnowledgeVectorRepository;
import com.linrun.domain.knowledgeasset.model.KnowledgeDocument;
import com.linrun.domain.knowledgeasset.model.KnowledgeFragment;
import com.linrun.domain.knowledgeasset.model.KnowledgeFragmentStatus;
import com.linrun.domain.knowledgeasset.model.KnowledgeVectorMaintenanceReport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeVectorMaintenanceServiceTest {

    @Test
    void shouldRebuildVersionVectors() {
        FakeKnowledgeDocumentRepository documentRepository = new FakeKnowledgeDocumentRepository();
        FakeKnowledgeVectorRepository vectorRepository = new FakeKnowledgeVectorRepository();
        KnowledgeVectorMaintenanceService service = service(documentRepository, vectorRepository);

        KnowledgeVectorMaintenanceReport report = service.rebuildVersion("v1");

        assertEquals("REBUILD", report.getAction());
        assertEquals(2, report.getFragmentCount());
        assertEquals(2, report.getSuccessCount());
        assertEquals(2, vectorRepository.savedFragments.size());
    }

    @Test
    void shouldEvaluateRecallHitRate() {
        FakeKnowledgeDocumentRepository documentRepository = new FakeKnowledgeDocumentRepository();
        FakeKnowledgeVectorRepository vectorRepository = new FakeKnowledgeVectorRepository();
        KnowledgeVectorMaintenanceService service = service(documentRepository, vectorRepository);
        service.rebuildVersion("v1");

        KnowledgeVectorMaintenanceReport report = service.evaluateRecall("拼团退款规则", List.of("KF10002"), 2);

        assertEquals("RECALL_EVALUATE", report.getAction());
        assertEquals(new BigDecimal("100.00"), report.getRecallHitRate());
        assertTrue(report.getHitFragmentIds().contains("KF10002"));
    }

    @Test
    void shouldCreateBackupSnapshot() {
        KnowledgeVectorMaintenanceService service = service(new FakeKnowledgeDocumentRepository(), new FakeKnowledgeVectorRepository());

        KnowledgeVectorMaintenanceReport report = service.backupVersion("v1");

        assertEquals("BACKUP", report.getAction());
        assertNotNull(report.getSnapshotId());
        assertEquals(2, report.getSuccessCount());
    }

    private KnowledgeVectorMaintenanceService service(FakeKnowledgeDocumentRepository documentRepository,
                                                      FakeKnowledgeVectorRepository vectorRepository) {
        return new KnowledgeVectorMaintenanceService(
                documentRepository,
                new KnowledgeVectorService(new FakeKnowledgeEmbeddingClient(), vectorRepository));
    }

    private static class FakeKnowledgeDocumentRepository implements KnowledgeDocumentRepository {

        private final List<KnowledgeFragment> fragments = List.of(
                fragment("KF10001", "轻薄学习平板标准版适合写论文和网课。"),
                fragment("KF10002", "拼团失败后系统自动退款。"));

        @Override
        public void save(KnowledgeDocument document, List<KnowledgeFragment> fragments) {
        }

        @Override
        public Optional<KnowledgeDocument> queryDocumentByDocumentId(String documentId) {
            return Optional.empty();
        }

        @Override
        public List<KnowledgeFragment> queryFragmentsByDocumentId(String documentId) {
            return fragments;
        }

        @Override
        public List<KnowledgeFragment> queryEnabledFragmentsByVersion(String knowledgeVersion) {
            return fragments.stream()
                    .filter(fragment -> fragment.getKnowledgeVersion().equals(knowledgeVersion))
                    .toList();
        }

        private static KnowledgeFragment fragment(String fragmentId, String content) {
            KnowledgeFragment fragment = new KnowledgeFragment();
            fragment.setFragmentId(fragmentId);
            fragment.setDocumentId("DOC10001");
            fragment.setGoodsId("G10001");
            fragment.setDocumentType("规则");
            fragment.setKnowledgeVersion("v1");
            fragment.setContent(content);
            fragment.setRankNo(1);
            fragment.setFragmentStatus(KnowledgeFragmentStatus.ENABLED);
            fragment.setEnabled(true);
            fragment.setCreateTime(LocalDateTime.now());
            return fragment;
        }
    }

    private static class FakeKnowledgeEmbeddingClient implements KnowledgeEmbeddingClient {

        @Override
        public List<Double> embed(String content) {
            if (content.contains("退款") || content.contains("拼团")) {
                return List.of(0.0d, 1.0d);
            }
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
            List<KnowledgeFragment> result = new ArrayList<>();
            for (int i = 0; i < savedFragments.size() && result.size() < limit; i++) {
                if (savedEmbeddings.get(i).equals(queryEmbedding)) {
                    result.add(savedFragments.get(i));
                }
            }
            return result;
        }
    }
}
