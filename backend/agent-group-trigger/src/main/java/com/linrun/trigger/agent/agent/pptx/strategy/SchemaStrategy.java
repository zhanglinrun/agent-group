package com.linrun.trigger.agent.agent.pptx.strategy;

import com.linrun.trigger.agent.entity.record.pptx.AiPptInst;
import com.linrun.trigger.agent.entity.record.pptx.AiPptTemplate;
import com.linrun.trigger.agent.entity.record.pptx.FieldData;
import com.linrun.trigger.agent.entity.record.pptx.PptInstStatus;
import com.linrun.trigger.agent.entity.record.pptx.PptSchema;
import com.linrun.trigger.agent.entity.record.pptx.Slide;
import com.linrun.trigger.agent.prompts.PptBuilderPrompts;
import com.linrun.trigger.agent.utils.ThinkTagParser;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Schema生成策略
 */
@Slf4j
public class SchemaStrategy implements PptStateStrategy {

    private static final PptInstStatus TARGET_STATUS = PptInstStatus.RENDER;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public void execute(AiPptInst inst, Sinks.Many<String> sink, String query,
                        StringBuilder thinkingBuffer, PptStateStrategyContext context) {
        sink.tryEmitNext(context.createThinkingResponse("正在设计PPT详细内容...\n"));

        String templateCode = inst.getTemplateCode();
        AiPptTemplate template = context.getPptTemplateService().getByCode(templateCode);
        String templateSchema = template.getTemplateSchema();
        String outline = inst.getOutline();

        String prompt = context.enhancePrompt(PptBuilderPrompts.getSchemaGenerationPrompt(templateSchema, outline));

        Disposable disposable = Mono.fromCallable(() -> {
                    String json = ThinkTagParser.stripThinkTags(
                            context.getChatModel().call(new Prompt(prompt)).getResult().getOutput().getText());
                    PptSchema pptSchema = parsePptSchema(json);
                    String pptSchemaJson = JSON.toJSONString(pptSchema);

                    context.getPptInstService().updatePptSchema(inst.getId(), pptSchemaJson, TARGET_STATUS);

                    // 处理图片生成
                    processImageGeneration(pptSchema, sink, inst.getConversationId(), context);

                    // 更新包含图片URL的schema
                    context.getPptInstService().updatePptSchema(inst.getId(), JSON.toJSONString(pptSchema), TARGET_STATUS);
                    context.continueStateMachine(inst, sink, query, thinkingBuffer);
                    return null;
                })
                .doOnError(err -> {
                    log.error("Schema生成异常", err);
                    // 失败时不回退状态，只更新错误信息，转到 FAILED
                    context.getPptInstService().updateError(inst.getId(),
                            "Schema生成失败: " + err.getMessage(), PptInstStatus.SCHEMA);
                    // 转到 FAILED 策略
                    PptStateStrategyFactory.getInstance().executeFailedState(inst, sink, query, thinkingBuffer, context);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        // 保存 disposable 到任务管理器，用于停止任??
        context.setDisposable(inst.getConversationId(), disposable);
    }

    /**
     * 执行 Schema 策略，支持修改模??
     *
     * @param inst PPT 实例
     * @param sink 输出 sink
     * @param query 用户查询
     * @param thinkingBuffer 思考缓??
     * @param context 策略上下??
     * @param modifyPrompt 修改提示词，如果??null 表示正常流程
     */
    public void executeWithModifyPrompt(AiPptInst inst, Sinks.Many<String> sink, String query,
                                        StringBuilder thinkingBuffer, PptStateStrategyContext context,
                                        String modifyPrompt) {
        sink.tryEmitNext(context.createThinkingResponse("正在重新生成PPT详细内容...\n"));

        Disposable disposable = Mono.fromCallable(() -> {
                    String json = ThinkTagParser.stripThinkTags(
                            context.getChatModel().call(new Prompt(context.enhancePrompt(modifyPrompt))).getResult().getOutput().getText());
                    PptSchema pptSchema = parsePptSchema(json);
                    String pptSchemaJson = JSON.toJSONString(pptSchema);

                    context.getPptInstService().updatePptSchema(inst.getId(), pptSchemaJson, TARGET_STATUS);

                    // 处理图片生成
                    processImageGeneration(pptSchema, sink, inst.getConversationId(), context);

                    // 更新包含图片URL的schema
                    context.getPptInstService().updatePptSchema(inst.getId(), JSON.toJSONString(pptSchema), TARGET_STATUS);
                    context.continueStateMachine(inst, sink, query, thinkingBuffer);
                    return null;
                })
                .doOnError(err -> {
                    log.error("Schema生成异常", err);
                    // 失败时不回退状态，只更新错误信息，转到 FAILED
                    context.getPptInstService().updateError(inst.getId(),
                            "Schema生成失败: " + err.getMessage(), PptInstStatus.SCHEMA);
                    // 转到 FAILED 策略
                    PptStateStrategyFactory.getInstance().executeFailedState(inst, sink, query, thinkingBuffer, context);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        // 保存 disposable 到任务管理器，用于停止任??
        context.setDisposable(inst.getConversationId(), disposable);
    }

    @Override
    public PptInstStatus getTargetStatus() {
        return TARGET_STATUS;
    }

    private PptSchema parsePptSchema(String rawJson) throws Exception {
        String normalizedJson = normalizeSchemaJson(rawJson);
        return OBJECT_MAPPER.readValue(normalizedJson, PptSchema.class);
    }

    private String normalizeSchemaJson(String rawJson) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(extractJsonObject(rawJson));
        JsonNode slides = root.get("slides");
        if (slides instanceof ArrayNode slideArray) {
            for (JsonNode slideNode : slideArray) {
                if (slideNode instanceof ObjectNode slideObject) {
                    normalizeTemplatePageIndex(slideObject);
                    normalizeSlideData(slideObject);
                }
            }
        }
        return OBJECT_MAPPER.writeValueAsString(root);
    }

    private String extractJsonObject(String rawJson) {
        if (rawJson == null) {
            throw new IllegalArgumentException("PPT Schema为空");
        }
        String trimmed = rawJson.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("PPT Schema不是合法JSON对象");
        }
        return trimmed.substring(start, end + 1);
    }

    private void normalizeTemplatePageIndex(ObjectNode slideObject) {
        JsonNode index = slideObject.get("templatePageIndex");
        if (index != null && index.isInt()) {
            return;
        }
        JsonNode pageIndex = slideObject.get("pageIndex");
        if (pageIndex != null && pageIndex.isInt()) {
            slideObject.put("templatePageIndex", pageIndex.asInt());
            return;
        }
        String pageType = text(slideObject.get("pageType")).toUpperCase();
        int templatePageIndex = switch (pageType) {
            case "COVER" -> 1;
            case "CATALOG" -> 2;
            case "COMPARE" -> 3;
            case "END" -> 5;
            default -> 4;
        };
        slideObject.put("templatePageIndex", templatePageIndex);
    }

    private void normalizeSlideData(ObjectNode slideObject) {
        JsonNode data = slideObject.get("data");
        if (!(data instanceof ObjectNode dataObject)) {
            slideObject.set("data", OBJECT_MAPPER.createObjectNode());
            return;
        }

        ObjectNode normalized = OBJECT_MAPPER.createObjectNode();
        String pageType = text(slideObject.get("pageType")).toUpperCase();
        int templatePageIndex = slideObject.path("templatePageIndex").asInt(4);

        dataObject.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                return;
            }
            if (value.isObject()) {
                normalized.set(key, normalizeField(key, (ObjectNode) value));
                return;
            }
            if (value.isArray()) {
                expandArrayField(normalized, key, (ArrayNode) value, pageType, templatePageIndex);
                return;
            }
            normalized.set(key, textField(value.asText(), defaultFontLimit(key, pageType, templatePageIndex)));
        });

        slideObject.set("data", normalized);
    }

    private ObjectNode normalizeField(String key, ObjectNode field) {
        ObjectNode normalized = OBJECT_MAPPER.createObjectNode();
        String type = text(field.get("type"));
        normalized.put("type", type.isBlank() ? "text" : type);
        JsonNode content = field.get("content");
        if (content == null || content.isNull()) {
            normalized.put("content", "");
        } else if (content.isValueNode()) {
            normalized.put("content", content.asText());
        } else {
            normalized.put("content", content.toString());
        }
        JsonNode fontLimit = field.get("fontLimit");
        normalized.put("fontLimit", fontLimit != null && fontLimit.canConvertToInt() ? fontLimit.asInt() : defaultFontLimit(key, "", 4));
        JsonNode url = field.get("url");
        if (url != null && !url.isNull()) {
            normalized.put("url", url.asText(""));
        }
        return normalized;
    }

    private void expandArrayField(ObjectNode normalized,
                                  String key,
                                  ArrayNode items,
                                  String pageType,
                                  int templatePageIndex) {
        List<String> values = values(items);
        if (values.isEmpty()) {
            return;
        }

        if ("catalogItems".equals(key)) {
            putCatalogItems(normalized, values);
            return;
        }
        if ("contentItems".equals(key)) {
            putContentItems(normalized, values, pageType, templatePageIndex);
            return;
        }
        normalized.set(key, textField(joinBullets(values), defaultFontLimit(key, pageType, templatePageIndex)));
    }

    private void putCatalogItems(ObjectNode normalized, List<String> values) {
        List<String> compact = compact(values, 3);
        for (int i = 0; i < compact.size(); i++) {
            normalized.set("catalog" + (i + 1), textField(compact.get(i), 24));
        }
    }

    private void putContentItems(ObjectNode normalized, List<String> values, String pageType, int templatePageIndex) {
        if ("COMPARE".equals(pageType) || templatePageIndex == 3) {
            List<String> compact = compact(values, 2);
            if (!compact.isEmpty()) {
                normalized.set("content1", textField(joinBullets(List.of(compact.get(0))), 120));
            }
            if (compact.size() > 1) {
                normalized.set("content2", textField(joinBullets(List.of(compact.get(1))), 120));
            }
            return;
        }
        normalized.set("content", textField(joinBullets(values), 180));
    }

    private List<String> values(ArrayNode items) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : items) {
            String value = "";
            if (item == null || item.isNull()) {
                continue;
            }
            if (item.isObject()) {
                value = text(item.get("content"));
            } else if (item.isValueNode()) {
                value = item.asText();
            } else {
                value = item.toString();
            }
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private List<String> compact(List<String> values, int maxItems) {
        if (values.size() <= maxItems) {
            return values;
        }
        List<String> compact = new ArrayList<>(values.subList(0, maxItems - 1));
        compact.add(String.join("\n", values.subList(maxItems - 1, values.size())));
        return compact;
    }

    private ObjectNode textField(String content, int fontLimit) {
        ObjectNode node = OBJECT_MAPPER.createObjectNode();
        node.put("type", "text");
        node.put("content", content == null ? "" : content);
        node.put("fontLimit", fontLimit);
        return node;
    }

    private String joinBullets(List<String> values) {
        return "- " + String.join("\n- ", values);
    }

    private int defaultFontLimit(String key, String pageType, int templatePageIndex) {
        if ("title".equals(key)) {
            return "COVER".equals(pageType) || templatePageIndex == 1 ? 30 : 40;
        }
        if ("description".equals(key)) {
            return 50;
        }
        if ("author".equals(key)) {
            return 40;
        }
        if (key != null && key.startsWith("catalog")) {
            return 24;
        }
        return 120;
    }

    private String text(JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText("");
    }

    /**
     * 处理图片生成
     */
    private void processImageGeneration(PptSchema pptSchema, Sinks.Many<String> sink, String conversationId,
                                        PptStateStrategyContext context) {
        if (pptSchema.getSlides() == null) {
            return;
        }

        // 首先收集所有需要生成图片的字段
        List<ImageGenerationTask> tasks = new ArrayList<>();
        for (Slide slide : pptSchema.getSlides()) {
            if (slide.getData() == null) {
                continue;
            }

            for (Map.Entry<String, FieldData> entry : slide.getData().entrySet()) {
                String key = entry.getKey();
                FieldData fieldData = entry.getValue();
                if (fieldData == null) {
                    continue;
                }

                String type = fieldData.getType();
                // 只处理image和background类型
                if (!"image".equalsIgnoreCase(type) && !"background".equalsIgnoreCase(type)) {
                    continue;
                }

                // 如果url已经有值，跳过
                if (fieldData.getUrl() != null && !fieldData.getUrl().isEmpty()) {
                    continue;
                }

                // url 为空，需要用 content 作为提示词生成图片。
                String prompt = fieldData.getContent();
                if (prompt == null || prompt.isEmpty()) {
                    continue;
                }

                tasks.add(new ImageGenerationTask(key, fieldData, prompt, slide));
            }
        }

        if (tasks.isEmpty()) {
            return;
        }

        int total = tasks.size();
        sink.tryEmitNext(context.createThinkingResponse("✅PPT内容设计完成，开始生成图片素材\n"));

        sink.tryEmitNext(context.createThinkingResponse("共需生成 " + total + " 张图片，开始生成...\n"));

        // 逐个生成图片
        for (int i = 0; i < tasks.size(); i++) {
            ImageGenerationTask task = tasks.get(i);
            int current = i + 1;

            sink.tryEmitNext(context.createThinkingResponse("正在生成图片 (" + current + "/" + total + ")... \n"));

            try {
                // 调用图片生成服务
                String originalImageUrl = context.getImageGenerationService().generateImage(task.prompt);

                // 下载图片并上传到MinIO
                byte[] imageBytes = downloadImageFromUrl(originalImageUrl);

                if (imageBytes != null && imageBytes.length > 0) {
                    // 上传到MinIO
                    String objectName = "ppt/" + conversationId + "/images/" + System.currentTimeMillis() + "_" + (i + 1) + ".png";
                    String minioUrl = context.getMinioService().uploadFile(objectName, imageBytes, "image/png");

                    // 更新schema中的url为MinIO地址
                    task.fieldData.setUrl(minioUrl);

                    sink.tryEmitNext(context.createThinkingResponse("图片生成完成 (" + current + "/" + total + ")\n"));
                    log.info("图片已上传到MinIO: {} -> {}", task.key, minioUrl);
                } else {
                    throw new RuntimeException("图片下载失败");
                }

            } catch (Exception e) {
                log.error("图片生成或上传失败 {}", task.prompt, e);
                sink.tryEmitNext(context.createThinkingResponse("图片生成失败 (" + current + "/" + total + "): \n" + task.key));
                // 使用空字符串
                task.fieldData.setUrl("");
            }
        }
        sink.tryEmitNext(context.createThinkingResponse("所有图片生成完成\n"));
        sink.tryEmitNext(context.createThinkingResponse("✅素材准备就绪，开始渲染PPT\n"));
    }

    /**
     * 从URL下载图片
     */
    private byte[] downloadImageFromUrl(String imageUrl) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
            return response.body();
        } else {
            throw new RuntimeException("下载图片失败，状态码: " + response.statusCode());
        }
    }

    /**
     * 图片生成任务
     */
    private static class ImageGenerationTask {
        String key;
        FieldData fieldData;
        String prompt;
        Slide slide;

        ImageGenerationTask(String key, FieldData fieldData, String prompt, Slide slide) {
            this.key = key;
            this.fieldData = fieldData;
            this.prompt = prompt;
            this.slide = slide;
        }
    }
}















