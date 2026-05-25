package com.linrun.infrastructure.knowledgeasset.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.knowledgeasset.adapter.KnowledgeEmbeddingClient;
import com.linrun.infrastructure.llm.OpenApiEndpointSupport;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class OpenApiKnowledgeEmbeddingClient implements KnowledgeEmbeddingClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiKnowledgeEmbeddingClient.class);

    @Value("${agent.group.llm.base-url:}")
    private String baseUrl;
    @Value("${agent.group.llm.api-key:}")
    private String apiKey;
    @Value("${agent.group.llm.embedding-model:text-embedding-v4}")
    private String embeddingModel;
    @Value("${agent.group.vector.dimension:1024}")
    private int dimension = 1024;
    @Value("${agent.group.llm.timeout-seconds:20}")
    private long timeoutSeconds = 20L;
    private Duration timeout = Duration.ofSeconds(20);
    private HttpClient httpClient = HttpClient.newHttpClient();
    private ObjectMapper objectMapper = new ObjectMapper();
    @Resource
    private LocalKnowledgeEmbeddingClient fallbackClient;
    @Resource
    private KnowledgeVectorMetrics metrics = KnowledgeVectorMetrics.noop();

    public OpenApiKnowledgeEmbeddingClient() {
    }

    public OpenApiKnowledgeEmbeddingClient(@Value("${agent.group.llm.base-url:}") String baseUrl,
                                           @Value("${agent.group.llm.api-key:}") String apiKey,
                                           @Value("${agent.group.llm.embedding-model:text-embedding-v4}") String embeddingModel,
                                           @Value("${agent.group.vector.dimension:1024}") int dimension,
                                           @Value("${agent.group.llm.timeout-seconds:20}") long timeoutSeconds,
                                           LocalKnowledgeEmbeddingClient fallbackClient,
                                           KnowledgeVectorMetrics metrics) {
        this(baseUrl, apiKey, embeddingModel, dimension, Duration.ofSeconds(Math.max(1L, timeoutSeconds)),
                HttpClient.newHttpClient(), new ObjectMapper(), fallbackClient, metrics);
    }

    OpenApiKnowledgeEmbeddingClient(String baseUrl,
                                    String apiKey,
                                    String embeddingModel,
                                    Duration timeout,
                                    HttpClient httpClient,
                                    ObjectMapper objectMapper,
                                    LocalKnowledgeEmbeddingClient fallbackClient) {
        this(baseUrl, apiKey, embeddingModel, 1024, timeout, httpClient, objectMapper, fallbackClient);
    }

    OpenApiKnowledgeEmbeddingClient(String baseUrl,
                                    String apiKey,
                                    String embeddingModel,
                                    int dimension,
                                    Duration timeout,
                                    HttpClient httpClient,
                                    ObjectMapper objectMapper,
                                    LocalKnowledgeEmbeddingClient fallbackClient) {
        this(baseUrl, apiKey, embeddingModel, dimension, timeout, httpClient, objectMapper,
                fallbackClient, KnowledgeVectorMetrics.noop());
    }

    OpenApiKnowledgeEmbeddingClient(String baseUrl,
                                    String apiKey,
                                    String embeddingModel,
                                    int dimension,
                                    Duration timeout,
                                    HttpClient httpClient,
                                    ObjectMapper objectMapper,
                                    LocalKnowledgeEmbeddingClient fallbackClient,
                                    KnowledgeVectorMetrics metrics) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
        this.dimension = Math.max(16, dimension);
        this.timeout = timeout;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.fallbackClient = fallbackClient;
        this.metrics = metrics == null ? KnowledgeVectorMetrics.noop() : metrics;
    }

    @PostConstruct
    private void initTimeout() {
        this.timeout = Duration.ofSeconds(Math.max(1L, timeoutSeconds));
        this.dimension = Math.max(16, dimension);
    }

    @Override
    public List<Double> embed(String content) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(baseUrl)) {
            metrics.recordEmbeddingFallback("llm_not_configured");
            return fallbackClient.embed(content);
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(embeddingsUri())
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(content)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("embedding call failed, status={}", response.statusCode());
                metrics.recordEmbeddingFallback("http_status_" + response.statusCode());
                return fallbackClient.embed(content);
            }
            List<Double> embedding = parseEmbedding(response.body());
            if (embedding.isEmpty()) {
                metrics.recordEmbeddingFallback("empty_embedding");
                return fallbackClient.embed(content);
            }
            return embedding;
        } catch (Exception e) {
            LOGGER.warn("embedding fallback, reason={}", e.getClass().getSimpleName());
            metrics.recordEmbeddingFallback(e.getClass().getSimpleName());
            return fallbackClient.embed(content);
        }
    }

    private URI embeddingsUri() {
        return OpenApiEndpointSupport.uri(baseUrl, "embeddings");
    }

    private String requestBody(String content) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", embeddingModel);
        body.put("input", StringUtils.hasText(content) ? content : "");
        body.put("dimensions", dimension);
        body.put("encoding_format", "float");
        return objectMapper.writeValueAsString(body);
    }

    private List<Double> parseEmbedding(String responseBody) throws IOException {
        JsonNode embeddingNode = objectMapper.readTree(responseBody)
                .path("data")
                .path(0)
                .path("embedding");
        if (!embeddingNode.isArray()) {
            return List.of();
        }
        List<Double> result = new ArrayList<>(embeddingNode.size());
        embeddingNode.forEach(node -> result.add(node.asDouble()));
        return result;
    }
}
