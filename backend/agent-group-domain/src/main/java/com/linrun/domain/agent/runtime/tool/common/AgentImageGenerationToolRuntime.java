package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolDefinition;
import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.output.AgentToolStructuredOutput;
import com.linrun.domain.agent.runtime.tool.port.AgentImageGenerationPort;
import com.linrun.types.exception.AppException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.defaultText;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.firstPresent;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.integer;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.stringList;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.text;

public class AgentImageGenerationToolRuntime {

    private final AgentImageGenerationPort imageGenerationPort;
    private final String imageBaseUrl;
    private final String imageApiKey;

    public AgentImageGenerationToolRuntime(AgentImageGenerationPort imageGenerationPort) {
        this(imageGenerationPort, "", "");
    }

    public AgentImageGenerationToolRuntime(AgentImageGenerationPort imageGenerationPort,
                                              String imageBaseUrl,
                                              String imageApiKey) {
        this.imageGenerationPort = imageGenerationPort;
        this.imageBaseUrl = text(imageBaseUrl);
        this.imageApiKey = text(imageApiKey);
    }

    public static AgentToolDefinition definition() {
        return AgentToolDefinition.builder(AgentToolOutputNames.IMAGE_GENERATION)
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

    public AgentToolStructuredOutput call(AgentToolCallCommand command) {
        if (imageGenerationPort == null) {
            throw new AppException("IMAGE_0001", "后端绘图模型异常，请检查图像模型配置后重试");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        AgentImageGenerationPort.AgentImageGenerationRequest request =
                new AgentImageGenerationPort.AgentImageGenerationRequest(
                        text(arguments.get("prompt")),
                        defaultText(arguments.get("mode"), "generate"),
                        defaultText(arguments.get("size"), "1024x1024"),
                        Math.max(1, Math.min(10, integer(arguments.get("batchCount"), 1))),
                        stringList(arguments.get("sourceImageUrls")),
                        stringList(arguments.get("maskImageUrls")),
                        defaultText(arguments.get("model"), AgentImageGenerationPort.DEFAULT_MODEL),
                        defaultText(arguments.get("quality"), AgentImageGenerationPort.DEFAULT_QUALITY),
                        defaultText(arguments.get("aspectRatio"), AgentImageGenerationPort.DEFAULT_ASPECT_RATIO),
                        imageBaseUrl,
                        imageApiKey);
        AgentImageGenerationPort.AgentImageGenerationResult result = imageGenerationPort.generate(request);
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
        List<AgentToolFileRef> fileRefs = fileRefs(result.fileRefs());

        return AgentToolStructuredOutput.builder(AgentToolOutputNames.IMAGE_GENERATION)
                .title("image generation")
                .summary(firstPresent(result.summary(), "generated " + fileRefs.size() + " image(s)"))
                .metadata(metadata)
                .fileRefs(fileRefs)
                .build();
    }

    private List<AgentToolFileRef> fileRefs(List<AgentToolFileRef> fileRefs) {
        return fileRefs == null ? List.of() : fileRefs;
    }
}















