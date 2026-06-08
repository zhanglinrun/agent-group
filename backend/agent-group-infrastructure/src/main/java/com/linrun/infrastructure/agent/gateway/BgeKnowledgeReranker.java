package com.linrun.infrastructure.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeReranker;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class BgeKnowledgeReranker implements KnowledgeReranker {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String endpoint;
    private final int topN;
    private final Duration timeout;

    public BgeKnowledgeReranker(ObjectMapper objectMapper,
                                @Value("${agent.group.rag.reranker.enabled:false}") boolean enabled,
                                @Value("${agent.group.rag.reranker.endpoint:}") String endpoint,
                                @Value("${agent.group.rag.reranker.top-n:5}") int topN,
                                @Value("${agent.group.rag.reranker.timeout-millis:1500}") long timeoutMillis) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(200L, timeoutMillis)))
                .build();
        this.enabled = enabled;
        this.endpoint = endpoint;
        this.topN = Math.max(1, topN);
        this.timeout = Duration.ofMillis(Math.max(200L, timeoutMillis));
    }

    @Override
    public List<GuideReference> rerank(String question, List<GuideReference> references, int limit) {
        if (references == null || references.isEmpty()) {
            return List.of();
        }
        int safeLimit = Math.max(1, limit);
        if (!enabled) {
            return references.stream().limit(safeLimit).toList();
        }
        if (StringUtils.hasText(endpoint)) {
            try {
                return rerankByRemoteBge(question, references, safeLimit);
            } catch (Exception ignored) {
                return rerankByLocalScore(question, references, safeLimit);
            }
        }
        return rerankByLocalScore(question, references, safeLimit);
    }

    private List<GuideReference> rerankByRemoteBge(String question,
                                                   List<GuideReference> references,
                                                   int limit) throws IOException, InterruptedException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", question);
        payload.put("texts", references.stream().map(reference -> safe(reference.getContent())).toList());
        payload.put("top_n", Math.min(topN, references.size()));
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("bge reranker status " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode results = root.has("results") ? root.get("results") : root.get("data");
        if (results == null || !results.isArray()) {
            throw new IOException("invalid bge reranker response");
        }
        List<ScoredReference> scoredReferences = new ArrayList<>();
        for (JsonNode item : results) {
            int index = item.has("index") ? item.get("index").asInt(-1) : -1;
            double score = item.has("score") ? item.get("score").asDouble(0D) : item.path("relevance_score").asDouble(0D);
            if (index >= 0 && index < references.size()) {
                scoredReferences.add(new ScoredReference(references.get(index), score));
            }
        }
        return rank(scoredReferences, limit);
    }

    private List<GuideReference> rerankByLocalScore(String question, List<GuideReference> references, int limit) {
        List<String> queryTerms = terms(question);
        List<ScoredReference> scoredReferences = new ArrayList<>();
        for (int i = 0; i < references.size(); i++) {
            GuideReference reference = references.get(i);
            double score = Math.max(0, 100 - i * 8) + lexicalScore(queryTerms, reference);
            scoredReferences.add(new ScoredReference(reference, score));
        }
        return rank(scoredReferences, limit);
    }

    private List<GuideReference> rank(List<ScoredReference> scoredReferences, int limit) {
        AtomicInteger rank = new AtomicInteger(1);
        return scoredReferences.stream()
                .sorted(Comparator.comparingDouble(ScoredReference::score).reversed())
                .limit(limit)
                .map(ScoredReference::reference)
                .peek(reference -> reference.setRank(rank.getAndIncrement()))
                .toList();
    }

    private double lexicalScore(List<String> queryTerms, GuideReference reference) {
        String content = safe(reference.getContent()).toLowerCase();
        String type = safe(reference.getDocumentType()).toLowerCase();
        double score = 0D;
        for (String term : queryTerms) {
            if (content.contains(term)) {
                score += 12D;
            }
            if (type.contains(term)) {
                score += 6D;
            }
        }
        return score;
    }

    private List<String> terms(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return List.of(value.toLowerCase().split("[\\s,，。；;:：!?！？、]+")).stream()
                .filter(StringUtils::hasText)
                .limit(12)
                .toList();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record ScoredReference(GuideReference reference, double score) {
    }
}
