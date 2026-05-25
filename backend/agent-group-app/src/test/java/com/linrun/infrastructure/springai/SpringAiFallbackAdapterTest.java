package com.linrun.infrastructure.springai;

import com.linrun.domain.conversation.model.GuideLlmResult;
import com.linrun.domain.conversation.model.GuideRagPrompt;
import com.linrun.domain.conversation.model.GuideTokenUsage;
import com.linrun.domain.knowledgeasset.model.KnowledgeFragment;
import com.linrun.infrastructure.knowledgeasset.vector.KnowledgeVectorMetrics;
import com.linrun.infrastructure.knowledgeasset.vector.LocalKnowledgeVectorRepository;
import com.linrun.infrastructure.knowledgeasset.vector.OpenApiKnowledgeEmbeddingClient;
import com.linrun.infrastructure.llm.OpenApiGuideLlmClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiFallbackAdapterTest {

    @Test
    void shouldFallbackToOpenApiLlmWhenSpringAiChatClientMissing() {
        SpringAiModelFactory factory = mock(SpringAiModelFactory.class);
        OpenApiGuideLlmClient fallbackClient = mock(OpenApiGuideLlmClient.class);
        GuideRagPrompt prompt = new GuideRagPrompt();
        GuideLlmResult fallbackResult = GuideLlmResult.of("兜底回答", GuideTokenUsage.empty(), 1L, true, "fallback");
        when(factory.chatClient()).thenReturn(Optional.empty());
        when(fallbackClient.completeWithMetrics(prompt)).thenReturn(fallbackResult);

        SpringAiGuideLlmClient client = new SpringAiGuideLlmClient(factory, fallbackClient,
                "qwen-plus", BigDecimal.ZERO, BigDecimal.ZERO);

        assertEquals(fallbackResult, client.completeWithMetrics(prompt));
        verify(fallbackClient).completeWithMetrics(prompt);
    }

    @Test
    void shouldFallbackToOpenApiEmbeddingWhenSpringAiEmbeddingModelMissing() {
        SpringAiModelFactory factory = mock(SpringAiModelFactory.class);
        OpenApiKnowledgeEmbeddingClient fallbackClient = mock(OpenApiKnowledgeEmbeddingClient.class);
        when(factory.embeddingModel()).thenReturn(Optional.empty());
        when(fallbackClient.embed("商品知识")).thenReturn(List.of(0.1D, 0.2D));

        SpringAiKnowledgeEmbeddingClient client = new SpringAiKnowledgeEmbeddingClient(
                factory, fallbackClient, KnowledgeVectorMetrics.noop());

        assertEquals(List.of(0.1D, 0.2D), client.embed("商品知识"));
        verify(fallbackClient).embed("商品知识");
    }

    @Test
    void shouldFallbackToLocalVectorRepositoryWhenSpringAiVectorStoreMissing() {
        SpringAiModelFactory factory = mock(SpringAiModelFactory.class);
        LocalKnowledgeVectorRepository fallbackRepository = mock(LocalKnowledgeVectorRepository.class);
        List<Double> queryEmbedding = List.of(0.1D, 0.2D);
        KnowledgeFragment fragment = new KnowledgeFragment();
        fragment.setFragmentId("KF10001");
        when(factory.vectorStore()).thenReturn(Optional.empty());
        when(fallbackRepository.searchSimilar(queryEmbedding, 3)).thenReturn(List.of(fragment));

        SpringAiKnowledgeVectorRepository repository = new SpringAiKnowledgeVectorRepository(
                factory, fallbackRepository, KnowledgeVectorMetrics.noop());

        assertEquals(List.of(fragment), repository.searchSimilar("拼团规则", queryEmbedding, 3));
        verify(fallbackRepository).searchSimilar(queryEmbedding, 3);
    }
}
