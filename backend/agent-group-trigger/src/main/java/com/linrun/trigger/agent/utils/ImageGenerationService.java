package com.linrun.trigger.agent.utils;

import com.linrun.trigger.agent.common.ImageProvider;
import com.linrun.trigger.agent.common.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class ImageGenerationService {

    // Qwen API配置（默认值，可被 agent-group.image.* 覆盖）
    private static final String DEFAULT_QWEN_API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private static final String DEFAULT_QWEN_MODEL = "qwen-image-plus";
    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    // 后台覆盖的图像模型配置（agent-group.image.*），未配则回退到默认 Qwen 链路。
    @Value("${agent-group.image.api-key:}")
    private String imageApiKey;
    @Value("${agent-group.image.base-url:}")
    private String imageBaseUrl;
    @Value("${agent-group.image.model:}")
    private String imageModel;

    // GrsAI nano-banana API配置
    @Value("${grsai.nanobanana.api-key:}")
    private String grsAiApiKey;
    private static final String GRS_AI_GENERATION_URL = "https://grsai.dakka.com.cn/v1/draw/nano-banana";

    /**
     * 生成图像（默认使用qwen）
     *
     * @param prompt 提示词
     * @return 图像URL
     */
    public String generateImage(String prompt) {
        return generateImage(prompt, ImageProvider.QWEN);
    }

    /**
     * 生成图像
     *
     * @param prompt   提示词
     * @param provider 图像生成服务提供者
     * @return 图像URL
     */
    public String generateImage(String prompt, ImageProvider provider) {
        if (provider == ImageProvider.QWEN) {
            return generateWithQwen(prompt);
        } else {
            return generateWithNanoBanana(prompt);
        }
    }

    /**
     * 使用通义千问生成图像（multimodal-generation 同步接口）
     */
    private String generateWithQwen(String prompt) {
        try {
            // 构建请求参数
            String effectiveApiKey = StringUtils.hasText(imageApiKey) ? imageApiKey : apiKey;
            String effectiveUrl = StringUtils.hasText(imageBaseUrl) ? imageBaseUrl : DEFAULT_QWEN_API_URL;
            String effectiveModel = StringUtils.hasText(imageModel) ? imageModel : DEFAULT_QWEN_MODEL;
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", effectiveModel);

            // input 使用 messages 格式
            Map<String, Object> textContent = new HashMap<>();
            textContent.put("text", prompt);

            Map<String, Object> userMessage = new HashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", new Object[]{textContent});

            Map<String, Object> input = new HashMap<>();
            input.put("messages", new Object[]{userMessage});
            requestBody.put("input", input);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("negative_prompt", "低分辨率，低画质，肢体畸形，手指畸形，画面过饱和，蜡像感，人脸无细节，过度光滑，画面具有AI感。构图混乱。文字模糊，扭曲。");
            parameters.put("prompt_extend", true);
            parameters.put("watermark", false);
            parameters.put("size", "1664*928");
            requestBody.put("parameters", parameters);

            // 创建HTTP请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(effectiveUrl))
                    .timeout(Duration.ofMinutes(5));

            // 添加请求头
            requestBuilder.header("Content-Type", "application/json");
            requestBuilder.header("Authorization", "Bearer " + effectiveApiKey);

            // 添加请求体
            String bodyStr = JsonUtils.toJson(requestBody);
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyStr));

            HttpRequest request = requestBuilder.build();

            // 发送请求
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode jsonResponse = JsonUtils.parse(response.body());
                log.info("Qwen图像生成响应: {}", jsonResponse);

                // 从响应中直接获取图片URL
                JsonNode output = jsonResponse != null ? jsonResponse.get("output") : null;
                if (output != null && output.has("choices")) {
                    JsonNode choices = output.get("choices");
                    if (choices != null && choices.isArray() && choices.size() > 0) {
                        JsonNode choice = choices.get(0);
                        JsonNode message = choice.get("message");
                        if (message != null && message.has("content")) {
                            JsonNode contents = message.get("content");
                            if (contents != null && contents.isArray() && contents.size() > 0) {
                                JsonNode content = contents.get(0);
                                if (content != null && content.has("image")) {
                                    String imageUrl = content.get("image").asText();
                                    log.info("Qwen图像生成成功，URL: {}", imageUrl);
                                    return imageUrl;
                                }
                            }
                        }
                    }
                }
            } else {
                log.error("Qwen HTTP请求失败，状态码: {}, 响应: {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Qwen图像生成失败", e);
        }
        return null;
    }

    /**
     * 使用nano-banana生成图像
     */
    private String generateWithNanoBanana(String prompt) {
        try {
            // 构建请求参数
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "nano-banana-pro");
            requestBody.put("prompt", prompt);
            requestBody.put("aspectRatio", "16:9");
            requestBody.put("imageSize", "1K");

            // 设置请求头
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer " + grsAiApiKey);

            // 创建HTTP请求
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(GRS_AI_GENERATION_URL))
                    .timeout(Duration.ofMinutes(5));

            // 添加请求头
            for (Map.Entry<String, String> header : headers.entrySet()) {
                requestBuilder.header(header.getKey(), header.getValue());
            }

            // 添加请求体
            String bodyStr = JsonUtils.toJson(requestBody);
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyStr));

            HttpRequest request = requestBuilder.build();

            // 发送请求并接收流式响应
            HttpResponse<java.io.InputStream> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                // 处理Server-Sent Events (SSE) 格式的流响应
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();

                        // 检查是否是数据行(data: ...)
                        if (line.startsWith("data: ")) {
                            String jsonData = line.substring(6).trim();

                            if (!jsonData.isEmpty() && !"[DONE]".equals(jsonData)) {
                                try {
                                    JsonNode jsonObject = JsonUtils.parse(jsonData);

                                    // 检查是否完成
                                    String status = jsonObject != null && jsonObject.has("status") ? jsonObject.get("status").asText() : null;
                                    if ("succeeded".equals(status)) {
                                        if (jsonObject.has("results") && jsonObject.get("results").isArray()
                                            && !jsonObject.get("results").isEmpty()) {
                                            JsonNode result = jsonObject.get("results").get(0);
                                            if (result != null && result.has("url")) {
                                                String imageUrl = result.get("url").asText();
                                                log.info("nano-banana图像生成成功，URL: {}", imageUrl);
                                                return imageUrl;
                                            }
                                        }
                                    } else if ("failed".equals(status) || "error".equals(status)) {
                                        log.error("nano-banana图像生成失败: {}",
                                                jsonObject != null && jsonObject.has("error") ? jsonObject.get("error").asText() : null);
                                        return null;
                                    }

                                    // 输出进度信息
                                    if (jsonObject != null && jsonObject.has("progress")) {
                                        int progress = jsonObject.get("progress").asInt();
                                        log.info("nano-banana图像生成进度: {}%", progress);
                                    }
                                } catch (Exception e) {
                                    log.error("解析SSE数据失败: {}", jsonData, e);
                                }
                            }
                        }
                    }
                }

                log.warn("流式响应结束，但未收到成功的图像生成结果");
                return null;
            } else {
                log.error("HTTP请求失败，状态码: {}", response.statusCode());
                return null;
            }

        } catch (Exception e) {
            log.error("nano-banana图像生成失败", e);
        }

        return null;
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
}















