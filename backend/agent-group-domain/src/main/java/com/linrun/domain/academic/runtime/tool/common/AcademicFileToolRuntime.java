package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicFileToolPort;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AcademicFileToolRuntime {

    private static final int DEFAULT_MAX_CONTENT_CHARS = 4000;

    private final AcademicFileToolPort fileToolPort;

    public AcademicFileToolRuntime(AcademicFileToolPort fileToolPort) {
        this.fileToolPort = fileToolPort;
    }

    public static AcademicToolDefinition definition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.FILE_TOOL)
                .description("Upload or read files through a configurable file artifact port.")
                .category("file")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "command", Map.of("type", "string", "description", "upload or get."),
                                "requestId", Map.of("type", "string", "description", "Request or session id."),
                                "fileName", Map.of("type", "string", "description", "Target file name."),
                                "description", Map.of("type", "string", "description", "File description."),
                                "content", Map.of("type", "string", "description", "File content for upload."),
                                "contentType", Map.of("type", "string", "description", "Content type."),
                                "maxContentChars", Map.of("type", "integer", "description", "Read content limit."),
                                "internalFile", Map.of("type", "boolean", "description", "Whether file is internal.")),
                        "required", List.of("command", "fileName")))
                .requiredArguments(List.of("command", "fileName"))
                .enabled(true)
                .build();
    }

    public AcademicToolStructuredOutput call(AcademicToolCallCommand command) {
        if (fileToolPort == null) {
            throw new AppException("FILE_TOOL_0001", "file tool port is not configured");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        String action = text(arguments.get("command")).toLowerCase(Locale.ROOT);
        AcademicFileToolPort.AcademicFileToolResult result;
        if ("upload".equals(action)) {
            result = fileToolPort.upload(new AcademicFileToolPort.AcademicFileUploadRequest(
                    text(arguments.get("requestId")),
                    text(arguments.get("fileName")),
                    text(arguments.get("description")),
                    text(arguments.get("content")),
                    defaultText(arguments.get("contentType"), "text/markdown"),
                    bool(arguments.get("internalFile"), false)));
        } else if ("get".equals(action) || "read".equals(action)) {
            result = fileToolPort.get(new AcademicFileToolPort.AcademicFileGetRequest(
                    text(arguments.get("requestId")),
                    text(arguments.get("fileName")),
                    Math.max(256, integer(arguments.get("maxContentChars"), DEFAULT_MAX_CONTENT_CHARS))));
            action = "get";
        } else {
            throw new AppException("FILE_TOOL_0002", "unsupported file command: " + action);
        }
        return project(action, arguments, result);
    }

    private AcademicToolStructuredOutput project(String action,
                                                 Map<String, Object> arguments,
                                                 AcademicFileToolPort.AcademicFileToolResult result) {
        if (result == null) {
            throw new AppException("FILE_TOOL_0003", "file tool returned empty result");
        }
        if (!result.success()) {
            throw new AppException("FILE_TOOL_0004", firstPresent(result.errorMessage(), "file tool failed"));
        }
        String fileName = firstPresent(result.fileName(), text(arguments.get("fileName")));
        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata() == null ? Map.of() : result.metadata());
        metadata.put("command", firstPresent(result.command(), action));
        metadata.put("fileName", fileName);

        return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.FILE_TOOL)
                .title(firstPresent(result.command(), action) + " " + fileName)
                .summary(firstPresent(result.summary(), fileName))
                .content(text(result.content()))
                .metadata(metadata)
                .fileRefs(fileRefs(result.fileRefs()))
                .build();
    }

    private List<AcademicToolFileRef> fileRefs(List<AcademicToolFileRef> fileRefs) {
        return fileRefs == null ? List.of() : fileRefs;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = text(value);
        return StringUtils.hasText(text) ? Boolean.parseBoolean(text) : fallback;
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















