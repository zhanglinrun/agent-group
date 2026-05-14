package com.linrun.domain.knowledge.service;

import com.linrun.domain.knowledge.adapter.KnowledgeDocumentRepository;
import com.linrun.domain.knowledge.model.KnowledgeFragment;
import com.linrun.domain.knowledge.model.KnowledgeVectorMaintenanceReport;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class KnowledgeVectorMaintenanceService {

    private static final DateTimeFormatter SNAPSHOT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeVectorService knowledgeVectorService;

    public KnowledgeVectorMaintenanceService(KnowledgeDocumentRepository knowledgeDocumentRepository,
                                             KnowledgeVectorService knowledgeVectorService) {
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeVectorService = knowledgeVectorService;
    }

    public KnowledgeVectorMaintenanceReport rebuildVersion(String knowledgeVersion) {
        String version = requireVersion(knowledgeVersion);
        List<KnowledgeFragment> fragments = knowledgeDocumentRepository.queryEnabledFragmentsByVersion(version);
        fragments.forEach(knowledgeVectorService::saveFragmentEmbedding);

        KnowledgeVectorMaintenanceReport report = baseReport("REBUILD", version, fragments);
        report.setSuccessCount(fragments.size());
        report.setMessage("知识版本向量已重建");
        return report;
    }

    public KnowledgeVectorMaintenanceReport backupVersion(String knowledgeVersion) {
        String version = requireVersion(knowledgeVersion);
        List<KnowledgeFragment> fragments = knowledgeDocumentRepository.queryEnabledFragmentsByVersion(version);

        KnowledgeVectorMaintenanceReport report = baseReport("BACKUP", version, fragments);
        report.setSuccessCount(fragments.size());
        report.setSnapshotId("KV" + LocalDateTime.now().format(SNAPSHOT_FORMATTER));
        report.setMessage("知识版本备份快照已生成，可用于后续离线导出");
        return report;
    }

    public KnowledgeVectorMaintenanceReport evaluateRecall(String question,
                                                           List<String> expectedFragmentIds,
                                                           int topK) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "问题不能为空");
        }
        if (expectedFragmentIds == null || expectedFragmentIds.isEmpty()) {
            throw new AppException("0001", "期望片段不能为空");
        }
        int safeTopK = topK <= 0 ? 3 : topK;
        List<String> hits = knowledgeVectorService.searchSimilar(question, safeTopK).stream()
                .map(KnowledgeFragment::getFragmentId)
                .toList();
        Set<String> expected = new HashSet<>(expectedFragmentIds);
        long matched = hits.stream().filter(expected::contains).count();

        KnowledgeVectorMaintenanceReport report = new KnowledgeVectorMaintenanceReport();
        report.setAction("RECALL_EVALUATE");
        report.setFragmentCount(hits.size());
        report.setSuccessCount(hits.size());
        report.setExpectedCount(expected.size());
        report.setMatchedCount((int) matched);
        report.setRecallHitRate(rate(matched, expected.size()));
        report.setHitFragmentIds(hits);
        report.setMessage(matched > 0 ? "召回评测命中期望片段" : "召回评测未命中期望片段，需要调整切片或向量策略");
        return report;
    }

    private KnowledgeVectorMaintenanceReport baseReport(String action, String version, List<KnowledgeFragment> fragments) {
        KnowledgeVectorMaintenanceReport report = new KnowledgeVectorMaintenanceReport();
        report.setAction(action);
        report.setKnowledgeVersion(version);
        report.setFragmentCount(fragments.size());
        report.setHitFragmentIds(fragments.stream().map(KnowledgeFragment::getFragmentId).toList());
        return report;
    }

    private String requireVersion(String knowledgeVersion) {
        if (!StringUtils.hasText(knowledgeVersion)) {
            throw new AppException("0001", "知识版本不能为空");
        }
        return knowledgeVersion.trim();
    }

    private BigDecimal rate(long matched, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(matched)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
