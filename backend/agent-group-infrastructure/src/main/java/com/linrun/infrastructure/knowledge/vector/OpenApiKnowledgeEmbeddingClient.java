package com.linrun.infrastructure.knowledge.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.knowledge.adapter.KnowledgeEmbeddingClient;
import com.linrun.infrastructure.llm.OpenApiEndpointSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
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

@Primary
@Component
public class OpenApiKnowledgeEmbeddingClient implements KnowledgeEmbeddingClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiKnowledgeEmbeddingClient.class);

    private final String baseUrl;
    private final String apiKey;
    private final String embeddingModel;
    private final int dimension;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LocalKnowledgeEmbeddingClient fallbackClient;

    @Autowired
    public OpenApiKnowledgeEmbeddingClient(@Value("${agent.group.llm.base-url:}") String baseUrl,
                                           @Value("${agent.group.llm.api-key:}") String apiKey,
                                           @Value("${agent.group.llm.embedding-model:text-embedding-v4}") String embeddingModel,
                                           @Value("${agent.group.vector.dimension:1024}") int dimension,
                                           @Value("${agent.group.llm.timeout-seconds:20}") long timeoutSeconds,
                                           LocalKnowledgeEmbeddingClient fallbackClient) {
        this(baseUrl, apiKey, embeddingModel, dimension, Duration.ofSeconds(Math.max(1L, timeoutSeconds)),
                HttpClient.newHttpClient(), new ObjectMapper(), fallbackClient);
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
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
        this.dimension = Math.max(16, dimension);
        this.timeout = timeout;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.fallbackClient = fallbackClient;
    }

    @Override
    public List<Double> embed(String content) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(baseUrl)) {
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
                return fallbackClient.embed(content);
            }
            List<Double> embedding = parseEmbedding(response.body());
            return embedding.isEmpty() ? fallbackClient.embed(content) : embedding;
        } catch (Exception e) {
            LOGGER.warn("embedding fallback, reason={}", e.getClass().getSimpleName());
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
