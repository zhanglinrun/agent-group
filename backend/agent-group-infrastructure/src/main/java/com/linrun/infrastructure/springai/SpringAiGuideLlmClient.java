package com.linrun.infrastructure.springai;

import com.linrun.domain.conversation.adapter.GuideLlmClient;
import com.linrun.domain.conversation.model.GuideLlmResult;
import com.linrun.domain.conversation.model.GuideRagPrompt;
import com.linrun.domain.conversation.model.GuideTokenUsage;
import com.linrun.infrastructure.llm.OpenApiGuideLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Primary
@Component
public class SpringAiGuideLlmClient implements GuideLlmClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiGuideLlmClient.class);

    private final SpringAiModelFactory modelFactory;
    private final OpenApiGuideLlmClient fallbackClient;
    private final String chatModel;
    private final BigDecimal promptTokenPriceYuanPer1k;
    private final BigDecimal completionTokenPriceYuanPer1k;

    public SpringAiGuideLlmClient(SpringAiModelFactory modelFactory,
                                  OpenApiGuideLlmClient fallbackClient,
                                  @Value("${agent.group.llm.chat-model:qwen-plus}") String chatModel,
                                  @Value("${agent.group.llm.prompt-token-price-yuan-per-1k:0}") BigDecimal promptTokenPriceYuanPer1k,
                                  @Value("${agent.group.llm.completion-token-price-yuan-per-1k:0}") BigDecimal completionTokenPriceYuanPer1k) {
        this.modelFactory = modelFactory;
        this.fallbackClient = fallbackClient;
        this.chatModel = chatModel;
        this.promptTokenPriceYuanPer1k = nonNegative(promptTokenPriceYuanPer1k);
        this.completionTokenPriceYuanPer1k = nonNegative(completionTokenPriceYuanPer1k);
    }

    @Override
    public String complete(GuideRagPrompt prompt) {
        return completeWithMetrics(prompt).getContent();
    }

    @Override
    public GuideLlmResult completeWithMetrics(GuideRagPrompt prompt) {
        Optional<ChatClient> chatClient = modelFactory.chatClient();
        if (chatClient.isEmpty()) {
            return fallbackClient.completeWithMetrics(prompt);
        }
        long startNanos = System.nanoTime();
        try {
            ChatResponse response = chatClient.get().prompt()
                    .system(prompt.getSystemPrompt())
                    .user(prompt.getUserPrompt())
                    .call()
                    .chatResponse();
            String content = content(response);
            GuideTokenUsage usage = usage(response);
            if (!StringUtils.hasText(content)) {
                return fallback(prompt, startNanos, usage);
            }
            return GuideLlmResult.of(content, usage, elapsedMillis(startNanos), false, model(response));
        } catch (Exception e) {
            LOGGER.warn("spring ai chat fallback, reason={}", e.getClass().getSimpleName());
            return fallbackClient.completeWithMetrics(prompt);
        }
    }

    @Override
    public GuideLlmResult streamWithMetrics(GuideRagPrompt prompt,
                                            Consumer<String> chunkSink,
                                            BooleanSupplier stopped) {
        Optional<ChatClient> chatClient = modelFactory.chatClient();
        if (chatClient.isEmpty()) {
            return fallbackClient.streamWithMetrics(prompt, chunkSink, stopped);
        }
        long startNanos = System.nanoTime();
        StringBuilder answer = new StringBuilder();
        GuideTokenUsage latestUsage = GuideTokenUsage.empty();
        String latestModel = chatModel;
        try {
            Iterable<ChatResponse> responses = chatClient.get().prompt()
                    .system(prompt.getSystemPrompt())
                    .user(prompt.getUserPrompt())
                    .stream()
                    .chatResponse()
                    .toIterable();
            for (ChatResponse response : responses) {
                if (isStopped(stopped)) {
                    break;
                }
                GuideTokenUsage usage = usage(response);
                if (usage.getTotalTokens() > 0L) {
                    latestUsage = usage;
                }
                latestModel = model(response);
                String chunk = content(response);
                if (!StringUtils.hasText(chunk)) {
                    continue;
                }
                answer.append(chunk);
                if (chunkSink != null) {
                    chunkSink.accept(chunk);
                }
            }
            if (!StringUtils.hasText(answer)) {
                return fallback(prompt, startNanos, latestUsage, chunkSink, stopped);
            }
            return GuideLlmResult.of(answer.toString(), latestUsage, elapsedMillis(startNanos), false, latestModel);
        } catch (Exception e) {
            LOGGER.warn("spring ai stream fallback, reason={}", e.getClass().getSimpleName());
            return fallbackClient.streamWithMetrics(prompt, chunkSink, stopped);
        }
    }

    private String content(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private String model(ChatResponse response) {
        if (response == null || response.getMetadata() == null
                || !StringUtils.hasText(response.getMetadata().getModel())) {
            return chatModel;
        }
        return response.getMetadata().getModel();
    }

    private GuideTokenUsage usage(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return GuideTokenUsage.empty();
        }
        Usage usage = response.getMetadata().getUsage();
        long promptTokens = nonNegative(usage.getPromptTokens());
        long completionTokens = nonNegative(usage.getCompletionTokens());
        long totalTokens = nonNegative(usage.getTotalTokens());
        if (totalTokens <= 0L) {
            totalTokens = promptTokens + completionTokens;
        }
        return new GuideTokenUsage(promptTokens, completionTokens, totalTokens,
                estimateCostYuan(promptTokens, completionTokens));
    }

    private long nonNegative(Integer value) {
        return value == null ? 0L : Math.max(0L, value.longValue());
    }

    private BigDecimal estimateCostYuan(long promptTokens, long completionTokens) {
        BigDecimal promptCost = BigDecimal.valueOf(Math.max(0L, promptTokens))
                .multiply(promptTokenPriceYuanPer1k)
                .divide(BigDecimal.valueOf(1000L), 8, RoundingMode.HALF_UP);
        BigDecimal completionCost = BigDecimal.valueOf(Math.max(0L, completionTokens))
                .multiply(completionTokenPriceYuanPer1k)
                .divide(BigDecimal.valueOf(1000L), 8, RoundingMode.HALF_UP);
        return promptCost.add(completionCost).setScale(6, RoundingMode.HALF_UP);
    }

    private GuideLlmResult fallback(GuideRagPrompt prompt, long startNanos, GuideTokenUsage tokenUsage) {
        return GuideLlmResult.of(prompt.getFallbackAnswer(), tokenUsage, elapsedMillis(startNanos), true, chatModel);
    }

    private GuideLlmResult fallback(GuideRagPrompt prompt,
                                    long startNanos,
                                    GuideTokenUsage tokenUsage,
                                    Consumer<String> chunkSink,
                                    BooleanSupplier stopped) {
        GuideLlmResult result = fallback(prompt, startNanos, tokenUsage);
        emitFallbackChunks(result.getContent(), chunkSink, stopped);
        return result;
    }

    private void emitFallbackChunks(String content, Consumer<String> chunkSink, BooleanSupplier stopped) {
        if (!StringUtils.hasText(content) || chunkSink == null || isStopped(stopped)) {
            return;
        }
        Arrays.stream(content.split("\\R+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .takeWhile(segment -> !isStopped(stopped))
                .forEach(segment -> chunkSink.accept(segment + "\n"));
    }

    private boolean isStopped(BooleanSupplier stopped) {
        return stopped != null && stopped.getAsBoolean();
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }
}
