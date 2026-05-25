package com.linrun.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.conversation.adapter.GuideLlmClient;
import com.linrun.domain.conversation.model.GuideLlmResult;
import com.linrun.domain.conversation.model.GuideRagPrompt;
import com.linrun.domain.conversation.model.GuideTokenUsage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Component
public class OpenApiGuideLlmClient implements GuideLlmClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiGuideLlmClient.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    @Value("${agent.group.llm.base-url:}")
    private String baseUrl;
    @Value("${agent.group.llm.api-key:}")
    private String apiKey;
    @Value("${agent.group.llm.chat-model:qwen-plus}")
    private String chatModel;
    @Value("${agent.group.llm.timeout-seconds:20}")
    private long timeoutSeconds = 20L;
    @Value("${agent.group.llm.max-retries:2}")
    private int maxRetries = 2;
    @Value("${agent.group.llm.min-interval-millis:200}")
    private long minIntervalMillis = 200L;
    @Value("${agent.group.llm.prompt-token-price-yuan-per-1k:0}")
    private BigDecimal promptTokenPriceYuanPer1k = BigDecimal.ZERO;
    @Value("${agent.group.llm.completion-token-price-yuan-per-1k:0}")
    private BigDecimal completionTokenPriceYuanPer1k = BigDecimal.ZERO;
    private Duration timeout = DEFAULT_TIMEOUT;
    private HttpClient httpClient = HttpClient.newHttpClient();
    private ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong lastCallMillis = new AtomicLong(0L);

    public OpenApiGuideLlmClient() {
    }

    public OpenApiGuideLlmClient(@Value("${agent.group.llm.base-url:}") String baseUrl,
                                 @Value("${agent.group.llm.api-key:}") String apiKey,
                                 @Value("${agent.group.llm.chat-model:qwen-plus}") String chatModel,
                                 @Value("${agent.group.llm.timeout-seconds:20}") long timeoutSeconds,
                                 @Value("${agent.group.llm.max-retries:2}") int maxRetries,
                                 @Value("${agent.group.llm.min-interval-millis:200}") long minIntervalMillis,
                                 @Value("${agent.group.llm.prompt-token-price-yuan-per-1k:0}") BigDecimal promptTokenPriceYuanPer1k,
                                 @Value("${agent.group.llm.completion-token-price-yuan-per-1k:0}") BigDecimal completionTokenPriceYuanPer1k) {
        this(baseUrl, apiKey, chatModel, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1L, timeoutSeconds)))
                .build(), new ObjectMapper(), Duration.ofSeconds(Math.max(1L, timeoutSeconds)),
                maxRetries, minIntervalMillis, promptTokenPriceYuanPer1k, completionTokenPriceYuanPer1k);
    }

    OpenApiGuideLlmClient(String baseUrl,
                          String apiKey,
                          String chatModel,
                          HttpClient httpClient,
                          ObjectMapper objectMapper) {
        this(baseUrl, apiKey, chatModel, httpClient, objectMapper, DEFAULT_TIMEOUT, 0, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    OpenApiGuideLlmClient(String baseUrl,
                          String apiKey,
                          String chatModel,
                          HttpClient httpClient,
                          ObjectMapper objectMapper,
                          Duration timeout,
                          int maxRetries,
                          long minIntervalMillis) {
        this(baseUrl, apiKey, chatModel, httpClient, objectMapper, timeout, maxRetries, minIntervalMillis,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    OpenApiGuideLlmClient(String baseUrl,
                          String apiKey,
                          String chatModel,
                          HttpClient httpClient,
                          ObjectMapper objectMapper,
                          Duration timeout,
                          int maxRetries,
                          long minIntervalMillis,
                          BigDecimal promptTokenPriceYuanPer1k,
                          BigDecimal completionTokenPriceYuanPer1k) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.maxRetries = Math.max(0, maxRetries);
        this.minIntervalMillis = Math.max(0L, minIntervalMillis);
        this.promptTokenPriceYuanPer1k = nonNegative(promptTokenPriceYuanPer1k);
        this.completionTokenPriceYuanPer1k = nonNegative(completionTokenPriceYuanPer1k);
    }

    @PostConstruct
    private void initRuntimeConfig() {
        this.timeout = Duration.ofSeconds(Math.max(1L, timeoutSeconds));
        this.maxRetries = Math.max(0, maxRetries);
        this.minIntervalMillis = Math.max(0L, minIntervalMillis);
        this.promptTokenPriceYuanPer1k = nonNegative(promptTokenPriceYuanPer1k);
        this.completionTokenPriceYuanPer1k = nonNegative(completionTokenPriceYuanPer1k);
    }

    @Override
    public String complete(GuideRagPrompt prompt) {
        return completeWithMetrics(prompt).getContent();
    }

    @Override
    public GuideLlmResult completeWithMetrics(GuideRagPrompt prompt) {
        long startNanos = System.nanoTime();
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(baseUrl)) {
            return fallback(prompt, startNanos);
        }

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                throttleIfNecessary();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(chatCompletionsUri())
                        .timeout(timeout)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody(prompt, false)))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    GuideLlmResult result = parseResult(response.body(), startNanos);
                    return StringUtils.hasText(result.getContent()) ? result : fallback(prompt, startNanos, result.getTokenUsage());
                }
                if (!shouldRetry(response.statusCode(), attempt)) {
                    LOGGER.warn("llm call failed without retry, status={}", response.statusCode());
                    return fallback(prompt, startNanos);
                }
                LOGGER.warn("llm call failed, status={}, attempt={}", response.statusCode(), attempt + 1);
                sleepBeforeRetry(attempt);
            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    LOGGER.warn("llm call exhausted retries, reason={}", e.getClass().getSimpleName());
                    return fallback(prompt, startNanos);
                }
                LOGGER.warn("llm call exception, attempt={}, reason={}", attempt + 1, e.getClass().getSimpleName());
                sleepBeforeRetry(attempt);
            }
        }
        return fallback(prompt, startNanos);
    }

    @Override
    public GuideLlmResult streamWithMetrics(GuideRagPrompt prompt,
                                            Consumer<String> chunkSink,
                                            BooleanSupplier stopped) {
        long startNanos = System.nanoTime();
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(baseUrl)) {
            return fallback(prompt, startNanos, GuideTokenUsage.empty(), chunkSink, stopped);
        }

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                throttleIfNecessary();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(chatCompletionsUri())
                        .timeout(timeout)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody(prompt, true)))
                        .build();
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    GuideLlmResult result = parseStreamResult(response.body(), startNanos, chunkSink, stopped);
                    return StringUtils.hasText(result.getContent())
                            ? result
                            : fallback(prompt, startNanos, result.getTokenUsage(), chunkSink, stopped);
                }
                if (!shouldRetry(response.statusCode(), attempt)) {
                    LOGGER.warn("llm stream call failed without retry, status={}", response.statusCode());
                    return fallback(prompt, startNanos, GuideTokenUsage.empty(), chunkSink, stopped);
                }
                LOGGER.warn("llm stream call failed, status={}, attempt={}", response.statusCode(), attempt + 1);
                sleepBeforeRetry(attempt);
            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    LOGGER.warn("llm stream call exhausted retries, reason={}", e.getClass().getSimpleName());
                    return fallback(prompt, startNanos, GuideTokenUsage.empty(), chunkSink, stopped);
                }
                LOGGER.warn("llm stream call exception, attempt={}, reason={}", attempt + 1, e.getClass().getSimpleName());
                sleepBeforeRetry(attempt);
            }
        }
        return fallback(prompt, startNanos, GuideTokenUsage.empty(), chunkSink, stopped);
    }

    private URI chatCompletionsUri() {
        return OpenApiEndpointSupport.uri(baseUrl, "chat/completions");
    }

    private String requestBody(GuideRagPrompt prompt, boolean stream) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", chatModel);
        body.put("stream", stream);
        body.put("temperature", 0.2);
        if (stream) {
            body.put("stream_options", Map.of("include_usage", true));
        }
        body.put("messages", List.of(
                message("system", prompt.getSystemPrompt()),
                message("user", prompt.getUserPrompt())
        ));
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, String> message(String role, String content) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private GuideLlmResult parseResult(String responseBody, long startNanos) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return GuideLlmResult.of("", parseUsage(root), elapsedMillis(startNanos), true, chatModel);
        }
        String content = choices.get(0).path("message").path("content").asText("");
        return GuideLlmResult.of(content, parseUsage(root), elapsedMillis(startNanos), false, chatModel);
    }

    private GuideLlmResult parseStreamResult(InputStream responseBody,
                                             long startNanos,
                                             Consumer<String> chunkSink,
                                             BooleanSupplier stopped) throws IOException {
        StringBuilder answer = new StringBuilder();
        GuideTokenUsage tokenUsage = GuideTokenUsage.empty();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(responseBody, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (isStopped(stopped)) {
                    break;
                }
                String data = streamData(line);
                if (!StringUtils.hasText(data)) {
                    continue;
                }
                if ("[DONE]".equals(data)) {
                    break;
                }
                JsonNode root = objectMapper.readTree(data);
                GuideTokenUsage usage = parseUsage(root);
                if (usage.getTotalTokens() > 0L) {
                    tokenUsage = usage;
                }
                String chunk = parseStreamContent(root);
                if (!StringUtils.hasText(chunk)) {
                    continue;
                }
                answer.append(chunk);
                if (chunkSink != null) {
                    chunkSink.accept(chunk);
                }
            }
        }
        return GuideLlmResult.of(answer.toString(), tokenUsage, elapsedMillis(startNanos), false, chatModel);
    }

    private String streamData(String line) {
        if (!StringUtils.hasText(line)) {
            return "";
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("data:")) {
            return trimmed.substring(5).trim();
        }
        return trimmed;
    }

    private String parseStreamContent(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "";
        }
        JsonNode choice = choices.get(0);
        String content = choice.path("delta").path("content").asText("");
        if (StringUtils.hasText(content)) {
            return content;
        }
        return choice.path("message").path("content").asText("");
    }

    private GuideTokenUsage parseUsage(JsonNode root) {
        JsonNode usage = root.path("usage");
        long promptTokens = firstLong(usage, "prompt_tokens", "input_tokens");
        long completionTokens = firstLong(usage, "completion_tokens", "output_tokens");
        long totalTokens = firstLong(usage, "total_tokens");
        if (totalTokens <= 0L) {
            totalTokens = promptTokens + completionTokens;
        }
        return new GuideTokenUsage(promptTokens, completionTokens, totalTokens,
                estimateCostYuan(promptTokens, completionTokens));
    }

    private long firstLong(JsonNode node, String... fieldNames) {
        if (node == null || node.isMissingNode()) {
            return 0L;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (value.canConvertToLong()) {
                return Math.max(0L, value.asLong());
            }
        }
        return 0L;
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

    private BigDecimal nonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private GuideLlmResult fallback(GuideRagPrompt prompt, long startNanos) {
        return fallback(prompt, startNanos, GuideTokenUsage.empty());
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

    private boolean shouldRetry(int statusCode, int attempt) {
        if (attempt >= maxRetries) {
            return false;
        }
        return statusCode == 429 || statusCode >= 500;
    }

    private void throttleIfNecessary() {
        if (minIntervalMillis <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long previous = lastCallMillis.getAndSet(now);
        long waitMillis = previous + minIntervalMillis - now;
        if (waitMillis > 0) {
            sleep(waitMillis);
        }
    }

    private void sleepBeforeRetry(int attempt) {
        sleep(Math.min(1000L, 100L * (attempt + 1)));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
