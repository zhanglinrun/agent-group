package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AcademicExternalSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AcademicExternalSearchService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${tavily.api-key:}")
    private String tavilyApiKey;

    public AcademicExternalSearchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean available() {
        return StringUtils.hasText(tavilyApiKey) && !tavilyApiKey.contains("XXXXX");
    }

    public List<SearchReference> search(String query, int limit) {
        if (!available() || !StringUtils.hasText(query)) {
            return List.of();
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("api_key", tavilyApiKey);
            body.put("query", query);
            body.put("search_depth", "basic");
            body.put("max_results", Math.max(1, Math.min(limit, 5)));
            body.put("include_answer", false);
            body.put("include_raw_content", false);

            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.tavily.com/search"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("academic external search skipped, status={}", response.statusCode());
                return List.of();
            }
            JsonNode results = objectMapper.readTree(response.body()).path("results");
            if (!results.isArray()) {
                return List.of();
            }
            List<SearchReference> references = new ArrayList<>();
            for (JsonNode item : results) {
                String url = item.path("url").asText("");
                String title = item.path("title").asText(url);
                String content = item.path("content").asText("");
                if (StringUtils.hasText(url) || StringUtils.hasText(content)) {
                    references.add(new SearchReference(title, url, content));
                }
            }
            return references;
        } catch (Exception e) {
            LOGGER.warn("academic external search failed, reason={}", e.getClass().getSimpleName());
            return List.of();
        }
    }

    public record SearchReference(String title, String url, String content) {
    }
}
