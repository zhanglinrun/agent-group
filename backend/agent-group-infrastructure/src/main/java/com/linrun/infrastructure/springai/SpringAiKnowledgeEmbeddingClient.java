package com.linrun.infrastructure.springai;

import com.linrun.domain.knowledgeasset.adapter.KnowledgeEmbeddingClient;
import com.linrun.infrastructure.knowledgeasset.vector.KnowledgeVectorMetrics;
import com.linrun.infrastructure.knowledgeasset.vector.OpenApiKnowledgeEmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Primary
@Component
public class SpringAiKnowledgeEmbeddingClient implements KnowledgeEmbeddingClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiKnowledgeEmbeddingClient.class);

    private final SpringAiModelFactory modelFactory;
    private final OpenApiKnowledgeEmbeddingClient fallbackClient;
    private final KnowledgeVectorMetrics metrics;

    public SpringAiKnowledgeEmbeddingClient(SpringAiModelFactory modelFactory,
                                            OpenApiKnowledgeEmbeddingClient fallbackClient,
                                            KnowledgeVectorMetrics metrics) {
        this.modelFactory = modelFactory;
        this.fallbackClient = fallbackClient;
        this.metrics = metrics == null ? KnowledgeVectorMetrics.noop() : metrics;
    }

    @Override
    public List<Double> embed(String content) {
        Optional<EmbeddingModel> embeddingModel = modelFactory.embeddingModel();
        if (embeddingModel.isEmpty()) {
            metrics.recordEmbeddingFallback("spring_ai_not_configured");
            return fallbackClient.embed(content);
        }
        try {
            float[] embedding = embeddingModel.get().embed(StringUtils.hasText(content) ? content : "");
            if (embedding == null || embedding.length == 0) {
                metrics.recordEmbeddingFallback("spring_ai_empty_embedding");
                return fallbackClient.embed(content);
            }
            List<Double> result = new ArrayList<>(embedding.length);
            for (float value : embedding) {
                result.add((double) value);
            }
            return result;
        } catch (Exception e) {
            LOGGER.warn("spring ai embedding fallback, reason={}", e.getClass().getSimpleName());
            metrics.recordEmbeddingFallback(e.getClass().getSimpleName());
            return fallbackClient.embed(content);
        }
    }
}
