package com.linrun.infrastructure.agent.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * table_rag 重排客户端：对外部 table_rag 召回的多张表 schema 做 DashScope gte-rerank 精排，
 * 选出与查询最相关的表，提升 nl2sql 选表准确率。
 * <p>
 * DashScope rerank 非 OpenAI 兼容，用 RestClient 直连 text-rerank 端点；
 * 不可用（关闭 / 无 api key）或调用失败时返回 null，由调用方退回原召回顺序，保证向后兼容。
 * <p>
 * api key 复用 LLM 同一套凭证（环境变量优先级与 application-dev.yml 一致）。
 */
@Slf4j
@Component
public class DashScopeRerankClient {

    private static final String RERANK_PATH = "/api/v1/services/rerank/text-rerank/text-rerank";
    private static final String BASE_URL = "https://dashscope.aliyuncs.com";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final int topN;
    private final boolean enabled;

    public DashScopeRerankClient(
            @Value("${AGENT_GROUP_LLM_API_KEY:${DASHSCOPE_API_KEY:${AI_BAILIAN_API_KEY:}}}") String apiKey,
            @Value("${agent.group.table-rag.rerank.enabled:false}") boolean enabled,
            @Value("${agent.group.table-rag.rerank.model:gte-rerank-v2}") String model,
            @Value("${agent.group.table-rag.rerank.top-n:5}") int topN,
            @Value("${agent.group.table-rag.rerank.timeout-ms:3000}") int timeoutMs) {
        this.objectMapper = new ObjectMapper();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = enabled;
        this.model = model;
        this.topN = Math.max(1, topN);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeout = timeoutMs > 0 ? timeoutMs : 3000;
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory).build();
        if (!isEnabled()) {
            log.info("[DashScopeRerankClient] table_rag rerank 未启用或缺少 api key，精排退回原召回顺序");
        }
    }

    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    int topN() {
        return topN;
    }

    /**
     * @param query 查询文本
     * @param docs  每张表的 schema 文本（与召回 matches 一一对应）
     * @return 与 docs 等长的相关性分（按 docs 顺序）；不可用或失败返回 null
     */
    public List<Double> rerank(String query, List<String> docs) {
        if (!isEnabled() || query == null || query.isBlank() || docs == null || docs.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "input", Map.of("query", query, "documents", docs),
                "parameters", Map.of("return_documents", false, "top_n", docs.size())
            );
            String responseText = restClient.post()
                .uri(RERANK_PATH)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);
            JsonNode response = (responseText == null || responseText.isBlank())
                ? null : objectMapper.readTree(responseText);
            return parseScores(response, docs.size());
        } catch (Exception e) {
            log.warn("[DashScopeRerankClient] table_rag rerank 调用失败，退回原召回顺序: {}", e.getMessage());
            return null;
        }
    }

    private List<Double> parseScores(JsonNode response, int size) {
        if (response == null) {
            return null;
        }
        JsonNode results = response.path("output").path("results");
        if (!results.isArray() || results.isEmpty()) {
            return null;
        }
        Double[] scores = new Double[size];
        Arrays.fill(scores, 0.0);
        for (JsonNode result : results) {
            int index = result.path("index").asInt(-1);
            if (index >= 0 && index < size) {
                scores[index] = result.path("relevance_score").asDouble(0.0);
            }
        }
        return new ArrayList<>(Arrays.asList(scores));
    }
}
