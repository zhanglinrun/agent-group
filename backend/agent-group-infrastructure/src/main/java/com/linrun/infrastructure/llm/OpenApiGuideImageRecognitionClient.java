package com.linrun.infrastructure.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.guide.adapter.GuideImageRecognitionClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
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
@Order(0)
public class OpenApiGuideImageRecognitionClient implements GuideImageRecognitionClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiGuideImageRecognitionClient.class);

    private final String baseUrl;
    private final String apiKey;
    private final String visionModel;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public OpenApiGuideImageRecognitionClient(@Value("${agent.group.llm.base-url:}") String baseUrl,
                                              @Value("${agent.group.llm.api-key:}") String apiKey,
                                              @Value("${agent.group.llm.vision-model:qwen3-vl-plus}") String visionModel,
                                              @Value("${agent.group.llm.timeout-seconds:20}") long timeoutSeconds) {
        this(baseUrl, apiKey, visionModel, Duration.ofSeconds(Math.max(1L, timeoutSeconds)),
                HttpClient.newHttpClient(), new ObjectMapper());
    }

    OpenApiGuideImageRecognitionClient(String baseUrl,
                                       String apiKey,
                                       String visionModel,
                                       Duration timeout,
                                       HttpClient httpClient,
                                       ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.visionModel = visionModel;
        this.timeout = timeout;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String recognize(String imageUrl) {
        if (!StringUtils.hasText(apiKey)
                || !StringUtils.hasText(baseUrl)
                || !StringUtils.hasText(imageUrl)
                || imageUrl.startsWith("local-image://")) {
            return "";
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(chatCompletionsUri())
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody(imageUrl)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.warn("image recognition failed, status={}", response.statusCode());
                return "";
            }
            String content = parseContent(response.body());
            return StringUtils.hasText(content) ? content : "";
        } catch (Exception e) {
            LOGGER.warn("image recognition fallback, reason={}", e.getClass().getSimpleName());
            return "";
        }
    }

    private URI chatCompletionsUri() {
        return OpenApiEndpointSupport.uri(baseUrl, "chat/completions");
    }

    private String requestBody(String imageUrl) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", visionModel);
        body.put("stream", false);
        body.put("temperature", 0.1);
        body.put("messages", List.of(message(imageUrl)));
        return objectMapper.writeValueAsString(body);
    }

    private Map<String, Object> message(String imageUrl) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", List.of(
                Map.of("type", "text", "text", "请识别图片里的商品名称、规格、价格、优惠、拼团或售后线索，用一句中文概括。"),
                Map.of("type", "image_url", "image_url", Map.of("url", imageUrl))
        ));
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
