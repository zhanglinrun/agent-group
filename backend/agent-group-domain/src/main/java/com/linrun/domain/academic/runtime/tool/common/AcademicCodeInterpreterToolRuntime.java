package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicCodeInterpreterPort;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AcademicCodeInterpreterToolRuntime {

    private final AcademicCodeInterpreterPort codeInterpreterPort;

    public AcademicCodeInterpreterToolRuntime(AcademicCodeInterpreterPort codeInterpreterPort) {
        this.codeInterpreterPort = codeInterpreterPort;
    }

    public static AcademicToolDefinition definition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.CODE_INTERPRETER)
                .description("Run code through a controlled code interpreter port and return stdout, stderr, explanation, and files.")
                .category("code")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "task", Map.of("type", "string", "description", "Task to complete."),
                                "language", Map.of("type", "string", "description", "Code language, such as python."),
                                "code", Map.of("type", "string", "description", "Code to execute."),
                                "fileNames", Map.of("type", "array", "description", "Input file names."),
                                "permissionProfile", Map.of(
                                        "type", "string",
                                        "enum", AcademicCodeInterpreterPort.allowedPermissionProfiles(),
                                        "default", AcademicCodeInterpreterPort.PERMISSION_PROFILE_ANALYSIS,
                                        "description", "Permission profile: analysis or workspace.")),
                        "required", List.of("task")))
                .requiredArguments(List.of("task"))
                .enabled(true)
                .build();
    }

    public AcademicToolStructuredOutput call(AcademicToolCallCommand command) {
        if (codeInterpreterPort == null) {
            throw new AppException("CODE_0001", "code interpreter port is not configured");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        AcademicCodeInterpreterPort.AcademicCodeExecutionRequest request =
                new AcademicCodeInterpreterPort.AcademicCodeExecutionRequest(
                        text(arguments.get("task")),
                        defaultText(arguments.get("language"), "python"),
                        text(arguments.get("code")),
                        stringList(arguments.get("fileNames")),
                        permissionProfile(arguments.get("permissionProfile")));
        AcademicCodeInterpreterPort.AcademicCodeExecutionResult result = codeInterpreterPort.execute(request);
        if (result == null) {
            throw new AppException("CODE_0002", "code interpreter returned empty result");
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("language", request.language());
        metadata.put("permissionProfile", request.permissionProfile());
        metadata.put("success", result.success());
        metadata.put("exitCode", result.exitCode() == null ? 0 : result.exitCode());
        metadata.put("stdout", text(result.stdout()));
        metadata.put("stderr", text(result.stderr()));
        metadata.put("code", text(result.code()));
        metadata.put("explain", text(result.explain()));

        String summary = result.success()
                ? firstPresent(result.explain(), result.content(), result.stdout(), "code executed")
                : firstPresent(result.stderr(), result.content(), "code execution failed");
        return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.CODE_INTERPRETER)
                .title(request.task())
                .summary(limit(summary))
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

    private String defaultText(Object value, String fallback) {
        String text = text(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String permissionProfile(Object value) {
        try {
            return AcademicCodeInterpreterPort.normalizePermissionProfile(text(value));
        } catch (IllegalArgumentException e) {
            throw new AppException("CODE_0003", "unsupported code interpreter permission profile");
        }
    }

    private String limit(String value) {
        String text = text(value);
        return text.length() <= 180 ? text : text.substring(0, 180);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
