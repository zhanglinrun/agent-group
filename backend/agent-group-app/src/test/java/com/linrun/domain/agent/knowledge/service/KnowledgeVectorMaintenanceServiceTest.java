package com.linrun.domain.agent.knowledge.service;

import com.linrun.domain.agent.knowledge.adapter.KnowledgeDocumentRepository;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeVectorRepository;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocument;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragmentStatus;
import com.linrun.domain.agent.knowledge.model.KnowledgeVectorMaintenanceReport;
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
                new KnowledgeVectorService(vectorRepository));
    }

    private static class FakeKnowledgeDocumentRepository implements KnowledgeDocumentRepository {

        private final List<KnowledgeFragment> fragments = List.of(
                fragment("KF10001", "基础学术额度包适合论文摘要和普通问答。"),
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

    private static class FakeKnowledgeVectorRepository implements KnowledgeVectorRepository {

        private final List<KnowledgeFragment> savedFragments = new ArrayList<>();

        @Override
        public void saveFragment(KnowledgeFragment fragment) {
            savedFragments.add(fragment);
        }

        @Override
        public List<KnowledgeFragment> searchSimilar(String question, int limit) {
            List<KnowledgeFragment> result = new ArrayList<>();
            for (KnowledgeFragment fragment : savedFragments) {
                if (result.size() >= limit) {
                    break;
                }
                if (fragment.getContent().contains("退款") || fragment.getContent().contains("拼团")) {
                    result.add(fragment);
                }
            }
            return result;
        }
    }
}
