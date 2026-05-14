package com.linrun.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.guide.adapter.GuideLlmClient;
import com.linrun.domain.guide.model.GuideRagPrompt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OpenApiGuideLlmClient implements GuideLlmClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiGuideLlmClient.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    private final String baseUrl;
    private final String apiKey;
    private final String chatModel;
    private final Duration timeout;
    private final int maxRetries;
    private final long minIntervalMillis;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AtomicLong lastCallMillis = new AtomicLong(0L);

    @Autowired
    public OpenApiGuideLlmClient(@Value("${agent.group.llm.base-url:}") String baseUrl,
                                 @Value("${agent.group.llm.api-key:}") String apiKey,
                                 @Value("${agent.group.llm.chat-model:qwen-plus}") String chatModel,
                                 @Value("${agent.group.llm.timeout-seconds:20}") long timeoutSeconds,
                                 @Value("${agent.group.llm.max-retries:2}") int maxRetries,
                                 @Value("${agent.group.llm.min-interval-millis:200}") long minIntervalMillis) {
        this(baseUrl, apiKey, chatModel, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1L, timeoutSeconds)))
                .build(), new ObjectMapper(), Duration.ofSeconds(Math.max(1L, timeoutSeconds)),
                maxRetries, minIntervalMillis);
    }

    OpenApiGuideLlmClient(String baseUrl,
                          String apiKey,
                          String chatModel,
                          HttpClient httpClient,
                          ObjectMapper objectMapper) {
        this(baseUrl, apiKey, chatModel, httpClient, objectMapper, DEFAULT_TIMEOUT, 0, 0L);
    }

    OpenApiGuideLlmClient(String baseUrl,
                          String apiKey,
                          String chatModel,
                          HttpClient httpClient,
                          ObjectMapper objectMapper,
                          Duration timeout,
                          int maxRetries,
                          long minIntervalMillis) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.maxRetries = Math.max(0, maxRetries);
        this.minIntervalMillis = Math.max(0L, minIntervalMillis);
    }

    @Override
    public String complete(GuideRagPrompt prompt) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(baseUrl)) {
            return prompt.getFallbackAnswer();
        }

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                throttleIfNecessary();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(chatCompletionsUri())
                        .timeout(timeout)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody(prompt)))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    String content = parseContent(response.body());
                    return StringUtils.hasText(content) ? content : prompt.getFallbackAnswer();
                }
                if (!shouldRetry(response.statusCode(), attempt)) {
                    LOGGER.warn("llm call failed without retry, status={}", response.statusCode());
                    return prompt.getFallbackAnswer();
                }
                LOGGER.warn("llm call failed, status={}, attempt={}", response.statusCode(), attempt + 1);
                sleepBeforeRetry(attempt);
            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    LOGGER.warn("llm call exhausted retries, reason={}", e.getClass().getSimpleName());
                    return prompt.getFallbackAnswer();
                }
                LOGGER.warn("llm call exception, attempt={}, reason={}", attempt + 1, e.getClass().getSimpleName());
                sleepBeforeRetry(attempt);
            }
        }
        return prompt.getFallbackAnswer();
    }

    private URI chatCompletionsUri() {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        return URI.create(normalizedBaseUrl + "v1/chat/completions");
    }

    private String requestBody(GuideRagPrompt prompt) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", chatModel);
        body.put("stream", false);
        body.put("temperature", 0.2);
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

    private String parseContent(String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            return "";
        }
        return choices.get(0).path("message").path("content").asText("");
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
