package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicMultimodalAnalysisPort;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AcademicMultimodalAgentToolRuntime {

    private final AcademicMultimodalAnalysisPort multimodalAnalysisPort;

    public AcademicMultimodalAgentToolRuntime(AcademicMultimodalAnalysisPort multimodalAnalysisPort) {
        this.multimodalAnalysisPort = multimodalAnalysisPort;
    }

    public static AcademicToolDefinition definition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.MULTIMODAL_AGENT)
                .description("Analyze text, images, and files through a configurable multimodal analysis port.")
                .category("multimodal")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "task", Map.of("type", "string", "description", "Analysis task."),
                                "text", Map.of("type", "string", "description", "Text context."),
                                "imageUrls", Map.of("type", "array", "description", "Image URLs."),
                                "fileUrls", Map.of("type", "array", "description", "File URLs.")),
                        "required", List.of("task")))
                .requiredArguments(List.of("task"))
                .enabled(true)
                .build();
    }

    public AcademicToolStructuredOutput call(AcademicToolCallCommand command) {
        if (multimodalAnalysisPort == null) {
            throw new AppException("MULTIMODAL_0001", "multimodal analysis port is not configured");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        AcademicMultimodalAnalysisPort.AcademicMultimodalAnalysisRequest request =
                new AcademicMultimodalAnalysisPort.AcademicMultimodalAnalysisRequest(
                        text(arguments.get("task")),
                        text(arguments.get("text")),
                        stringList(arguments.get("imageUrls")),
                        stringList(arguments.get("fileUrls")));
        AcademicMultimodalAnalysisPort.AcademicMultimodalAnalysisResult result =
                multimodalAnalysisPort.analyze(request);
        if (result == null) {
            throw new AppException("MULTIMODAL_0002", "multimodal analysis returned empty result");
        }
        if (!result.success()) {
            throw new AppException("MULTIMODAL_0003", firstPresent(result.errorMessage(), "multimodal analysis failed"));
        }

        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata() == null ? Map.of() : result.metadata());
        metadata.put("task", request.task());
        metadata.put("imageCount", request.imageUrls().size());
        metadata.put("fileCount", request.fileUrls().size());

        return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.MULTIMODAL_AGENT)
                .title(request.task())
                .summary(firstPresent(result.summary(), result.content()))
                .content(text(result.content()))
                .metadata(metadata)
                .fileRefs(fileRefs(result.fileRefs()))
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
