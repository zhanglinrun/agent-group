package com.linrun.infrastructure.springai;

import com.linrun.domain.knowledgeasset.adapter.KnowledgeVectorRepository;
import com.linrun.domain.knowledgeasset.model.KnowledgeFragment;
import com.linrun.domain.knowledgeasset.model.KnowledgeFragmentStatus;
import com.linrun.infrastructure.knowledgeasset.vector.LocalKnowledgeVectorRepository;
import com.linrun.infrastructure.knowledgeasset.vector.KnowledgeVectorMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Primary
@Repository
public class SpringAiKnowledgeVectorRepository implements KnowledgeVectorRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiKnowledgeVectorRepository.class);

    private final SpringAiModelFactory modelFactory;
    private final LocalKnowledgeVectorRepository fallbackRepository;
    private final KnowledgeVectorMetrics metrics;

    public SpringAiKnowledgeVectorRepository(SpringAiModelFactory modelFactory,
                                             LocalKnowledgeVectorRepository fallbackRepository,
                                             KnowledgeVectorMetrics metrics) {
        this.modelFactory = modelFactory;
        this.fallbackRepository = fallbackRepository;
        this.metrics = metrics == null ? KnowledgeVectorMetrics.noop() : metrics;
    }

    @Override
    public void saveEmbedding(KnowledgeFragment fragment, List<Double> embedding) {
        boolean springAiSaved = false;
        Optional<VectorStore> vectorStore = modelFactory.vectorStore();
        if (vectorStore.isPresent() && fragment != null && StringUtils.hasText(fragment.getContent())) {
            try {
                vectorStore.get().add(List.of(document(fragment)));
                springAiSaved = true;
            } catch (Exception e) {
                LOGGER.warn("spring ai vector save fallback, fragmentId={}, reason={}",
                        fragment.getFragmentId(), e.getClass().getSimpleName());
                metrics.recordLocalFallback("spring_ai_vector_save_failed");
            }
        }
        fallbackRepository.saveEmbedding(fragment, embedding);
        if (!springAiSaved && vectorStore.isEmpty()) {
            metrics.recordLocalFallback("spring_ai_vector_not_configured");
        }
    }

    @Override
    public List<KnowledgeFragment> searchSimilar(List<Double> queryEmbedding, int limit) {
        return fallbackRepository.searchSimilar(queryEmbedding, limit);
    }

    @Override
    public List<KnowledgeFragment> searchSimilar(String question, List<Double> queryEmbedding, int limit) {
        Optional<VectorStore> vectorStore = modelFactory.vectorStore();
        if (vectorStore.isEmpty() || !StringUtils.hasText(question)) {
            metrics.recordLocalFallback("spring_ai_vector_not_configured");
            return fallbackRepository.searchSimilar(queryEmbedding, limit);
        }
        try {
            List<Document> documents = vectorStore.get().similaritySearch(SearchRequest.builder()
                    .query(question)
                    .topK(Math.max(1, limit))
                    .similarityThresholdAll()
                    .build());
            if (documents == null || documents.isEmpty()) {
                metrics.recordLocalFallback("spring_ai_vector_empty");
                return fallbackRepository.searchSimilar(queryEmbedding, limit);
            }
            List<KnowledgeFragment> fragments = new ArrayList<>(documents.size());
            int rank = 1;
            for (Document document : documents) {
                fragments.add(fragment(document, rank++));
            }
            return fragments;
        } catch (Exception e) {
            LOGGER.warn("spring ai vector search fallback, reason={}", e.getClass().getSimpleName());
            metrics.recordLocalFallback(e.getClass().getSimpleName());
            return fallbackRepository.searchSimilar(queryEmbedding, limit);
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
}
