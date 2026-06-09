package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicImageGenerationPort;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AcademicImageGenerationToolRuntime {

    private final AcademicImageGenerationPort imageGenerationPort;
    private final String imageBaseUrl;
    private final String imageApiKey;

    public AcademicImageGenerationToolRuntime(AcademicImageGenerationPort imageGenerationPort) {
        this(imageGenerationPort, "", "");
    }

    public AcademicImageGenerationToolRuntime(AcademicImageGenerationPort imageGenerationPort,
                                              String imageBaseUrl,
                                              String imageApiKey) {
        this.imageGenerationPort = imageGenerationPort;
        this.imageBaseUrl = text(imageBaseUrl);
        this.imageApiKey = text(imageApiKey);
    }

    public static AcademicToolDefinition definition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.IMAGE_GENERATION)
                .description("Generate or edit images through a configurable image generation port.")
                .category("image")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "prompt", Map.of("type", "string", "description", "Image prompt."),
                                "mode", Map.of("type", "string", "description", "generate or edit."),
                                "model", Map.of("type", "string", "description", "Image generation model."),
                                "quality", Map.of("type", "string", "description", "Image quality level."),
                                "aspectRatio", Map.of("type", "string", "description", "Output aspect ratio."),
                                "size", Map.of("type", "string", "description", "Output size."),
                                "batchCount", Map.of("type", "integer", "description", "Number of images."),
                                "sourceImageUrls", Map.of("type", "array", "description", "Source images for editing."),
                                "maskImageUrls", Map.of("type", "array", "description", "Mask images for editing.")),
                        "required", List.of("prompt")))
                .requiredArguments(List.of("prompt"))
                .enabled(true)
                .build();
    }

    public AcademicToolStructuredOutput call(AcademicToolCallCommand command) {
        if (imageGenerationPort == null) {
            throw new AppException("IMAGE_0001", "后端绘图模型异常，请检查图像模型配置后重试");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        AcademicImageGenerationPort.AcademicImageGenerationRequest request =
                new AcademicImageGenerationPort.AcademicImageGenerationRequest(
                        text(arguments.get("prompt")),
                        defaultText(arguments.get("mode"), "generate"),
                        defaultText(arguments.get("size"), "1024x1024"),
                        Math.max(1, Math.min(10, integer(arguments.get("batchCount"), 1))),
                        stringList(arguments.get("sourceImageUrls")),
                        stringList(arguments.get("maskImageUrls")),
                        defaultText(arguments.get("model"), AcademicImageGenerationPort.DEFAULT_MODEL),
                        defaultText(arguments.get("quality"), AcademicImageGenerationPort.DEFAULT_QUALITY),
                        defaultText(arguments.get("aspectRatio"), AcademicImageGenerationPort.DEFAULT_ASPECT_RATIO),
                        imageBaseUrl,
                        imageApiKey);
        AcademicImageGenerationPort.AcademicImageGenerationResult result = imageGenerationPort.generate(request);
        if (result == null) {
            throw new AppException("IMAGE_0002", "后端绘图模型异常，请检查图像模型配置后重试");
        }
        if (!result.success()) {
            throw new AppException("IMAGE_0003", "后端绘图模型异常，请检查图像模型配置后重试");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("prompt", request.prompt());
        metadata.put("mode", request.mode());
        metadata.put("model", request.model());
        metadata.put("quality", request.quality());
        metadata.put("aspectRatio", request.aspectRatio());
        metadata.put("size", request.size());
        metadata.put("batchCount", request.batchCount());
        metadata.put("provider", text(result.provider()));
        metadata.put("sourceImageCount", request.sourceImageUrls().size());
        metadata.put("maskImageCount", request.maskImageUrls().size());
        List<AcademicToolFileRef> fileRefs = fileRefs(result.fileRefs());

        return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.IMAGE_GENERATION)
                .title("image generation")
                .summary(firstPresent(result.summary(), "generated " + fileRefs.size() + " image(s)"))
                .metadata(metadata)
                .fileRefs(fileRefs)
                .build();
    }

    private List<AcademicToolFileRef> fileRefs(List<AcademicToolFileRef> fileRefs) {
        return fileRefs == null ? List.of() : fileRefs;
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(this::text)
                .filter(StringUtils::hasText)
                .toList();
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String defaultText(Object value, String fallback) {
        String text = text(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}















