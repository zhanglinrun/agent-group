package com.linrun.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.guide.adapter.GuideLlmClient;
import com.linrun.domain.guide.model.GuideRagPrompt;
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

@Component
public class OpenApiGuideLlmClient implements GuideLlmClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(20);

    private final String baseUrl;
    private final String apiKey;
    private final String chatModel;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenApiGuideLlmClient(@Value("${agent.group.llm.base-url:}") String baseUrl,
                                 @Value("${agent.group.llm.api-key:}") String apiKey,
                                 @Value("${agent.group.llm.chat-model:qwen-plus}") String chatModel) {
        this(baseUrl, apiKey, chatModel, HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .build(), new ObjectMapper());
    }

    OpenApiGuideLlmClient(String baseUrl,
                          String apiKey,
                          String chatModel,
                          HttpClient httpClient,
                          ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String complete(GuideRagPrompt prompt) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(baseUrl)) {
            return prompt.getFallbackAnswer();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(chatCompletionsUri())
                    .timeout(DEFAULT_TIMEOUT)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(prompt)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return prompt.getFallbackAnswer();
            }
            String content = parseContent(response.body());
            return StringUtils.hasText(content) ? content : prompt.getFallbackAnswer();
        } catch (Exception e) {
            return prompt.getFallbackAnswer();
        }
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
}
