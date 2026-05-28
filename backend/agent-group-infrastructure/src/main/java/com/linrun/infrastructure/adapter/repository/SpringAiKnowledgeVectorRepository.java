package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.agent.knowledge.adapter.KnowledgeVectorRepository;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragmentStatus;
import com.linrun.infrastructure.gateway.KnowledgeVectorMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class SpringAiKnowledgeVectorRepository implements KnowledgeVectorRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiKnowledgeVectorRepository.class);

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final KnowledgeVectorMetrics metrics;

    public SpringAiKnowledgeVectorRepository(ObjectProvider<VectorStore> vectorStoreProvider,
                                             KnowledgeVectorMetrics metrics) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.metrics = metrics == null ? KnowledgeVectorMetrics.noop() : metrics;
    }

    @Override
    public void saveFragment(KnowledgeFragment fragment) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null || fragment == null || !StringUtils.hasText(fragment.getContent())) {
            metrics.recordVectorIssue("spring_ai_vector_not_available");
            return;
        }
        long startNanos = System.nanoTime();
        try {
            vectorStore.add(List.of(document(fragment)));
            metrics.recordPgvectorSave(true, elapsedMillis(startNanos));
        } catch (Exception e) {
            LOGGER.warn("spring ai vector save failed, fragmentId={}, reason={}",
                    fragment.getFragmentId(), e.getClass().getSimpleName());
            metrics.recordPgvectorSave(false, elapsedMillis(startNanos));
            metrics.recordVectorIssue("spring_ai_vector_save_failed");
        }
    }

    @Override
    public List<KnowledgeFragment> searchSimilar(String question, int limit) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null || !StringUtils.hasText(question)) {
            metrics.recordVectorIssue("spring_ai_vector_not_available");
            return List.of();
        }
        long startNanos = System.nanoTime();
        try {
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(Math.max(1, limit))
                    .similarityThresholdAll()
                    .build());
            metrics.recordPgvectorSearch(true, elapsedMillis(startNanos));
            if (documents == null || documents.isEmpty()) {
                metrics.recordVectorIssue("spring_ai_vector_empty");
                return List.of();
            }
            List<KnowledgeFragment> fragments = new ArrayList<>(documents.size());
            int rank = 1;
            for (Document document : documents) {
                fragments.add(fragment(document, rank++));
            }
            return fragments;
        } catch (Exception e) {
            LOGGER.warn("spring ai vector search failed, reason={}", e.getClass().getSimpleName());
            metrics.recordPgvectorSearch(false, elapsedMillis(startNanos));
            metrics.recordVectorIssue(e.getClass().getSimpleName());
            return List.of();
        }
    }

    private Document document(KnowledgeFragment fragment) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("fragmentId", safe(fragment.getFragmentId()));
        metadata.put("documentId", safe(fragment.getDocumentId()));
        metadata.put("goodsId", safe(fragment.getGoodsId()));
        metadata.put("documentType", safe(fragment.getDocumentType()));
        metadata.put("knowledgeVersion", safe(fragment.getKnowledgeVersion()));
        metadata.put("rankNo", fragment.getRankNo() == null ? 0 : fragment.getRankNo());
        metadata.put("parentFragmentId", safe(fragment.getParentFragmentId()));
        metadata.put("brotherGroupId", safe(fragment.getBrotherGroupId()));
        metadata.put("brotherIndex", fragment.getBrotherIndex() == null ? 1 : fragment.getBrotherIndex());
        metadata.put("brotherTotal", fragment.getBrotherTotal() == null ? 1 : fragment.getBrotherTotal());
        metadata.put("chunkType", safe(fragment.getChunkType()));
        return Document.builder()
                .id(fragment.getFragmentId())
                .text(fragment.getContent())
                .metadata(metadata)
                .build();
    }

    private KnowledgeFragment fragment(Document document, int rank) {
        Map<String, Object> metadata = document.getMetadata() == null ? Map.of() : document.getMetadata();
        KnowledgeFragment fragment = new KnowledgeFragment();
        fragment.setFragmentId(text(metadata, "fragmentId", document.getId()));
        fragment.setDocumentId(text(metadata, "documentId", ""));
        fragment.setGoodsId(text(metadata, "goodsId", ""));
        fragment.setDocumentType(text(metadata, "documentType", "Spring AI 向量检索"));
        fragment.setKnowledgeVersion(text(metadata, "knowledgeVersion", "v1"));
        fragment.setContent(document.getText());
        fragment.setRankNo(rank);
        fragment.setParentFragmentId(text(metadata, "parentFragmentId", ""));
        fragment.setBrotherGroupId(text(metadata, "brotherGroupId", ""));
        fragment.setBrotherIndex(number(metadata, "brotherIndex", 1));
        fragment.setBrotherTotal(number(metadata, "brotherTotal", 1));
        fragment.setChunkType(text(metadata, "chunkType", "CHILD"));
        fragment.setEmbeddingEnabled(true);
        fragment.setFragmentStatus(KnowledgeFragmentStatus.ENABLED);
        fragment.setEnabled(true);
        return fragment;
    }

    private String text(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        return value == null ? fallback : value.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Integer number(Map<String, Object> metadata, String key, int fallback) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
