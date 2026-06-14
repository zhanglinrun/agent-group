package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicScriptRunnerPort;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AcademicScriptRunnerToolRuntime {

    private static final int DEFAULT_TIMEOUT_SECONDS = 120;
    private static final int MAX_TIMEOUT_SECONDS = 300;
    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_PATH_LENGTH = 240;
    private static final int MAX_ARGV_COUNT = 20;
    private static final int MAX_ARGV_ITEM_LENGTH = 256;
    private static final Set<String> ALLOWED_RUNTIMES = Set.of("python", "node", "shell", "powershell", "bat");

    private final AcademicScriptRunnerPort scriptRunnerPort;

    public AcademicScriptRunnerToolRuntime(AcademicScriptRunnerPort scriptRunnerPort) {
        this.scriptRunnerPort = scriptRunnerPort;
    }

    public static AcademicToolDefinition definition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.SCRIPT_RUNNER)
                .description("Run a registered skill script through a controlled script runner port.")
                .category("skill")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "requestId", Map.of("type", "string", "description", "Request or session id."),
                                "skillName", Map.of("type", "string", "description", "Skill name."),
                                "skillBasePath", Map.of("type", "string", "description", "Registered skill base path."),
                                "scriptName", Map.of("type", "string", "description", "Script display name."),
                                "scriptPath", Map.of("type", "string", "description", "Script path inside skill."),
                                "runtime", Map.of("type", "string", "description", "python, node, shell, powershell, or bat."),
                                "arguments", Map.of("type", "object", "description", "Script arguments."),
                                "argv", Map.of("type", "array", "description", "Command-line arguments."),
                                "timeoutSeconds", Map.of("type", "integer", "description", "Execution timeout.")),
                        "required", List.of("skillName", "scriptName")))
                .requiredArguments(List.of("skillName", "scriptName"))
                .enabled(true)
                .build();
    }

    public AcademicToolStructuredOutput call(AcademicToolCallCommand command) {
        if (scriptRunnerPort == null) {
            throw new AppException("SCRIPT_RUNNER_0001", "script runner port is not configured");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        AcademicScriptRunnerPort.AcademicScriptRunRequest request = validatedRequest(arguments);
        AcademicScriptRunnerPort.AcademicScriptRunResult result = scriptRunnerPort.run(request);
        if (result == null) {
            throw new AppException("SCRIPT_RUNNER_0002", "script runner returned empty result");
        }

        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata() == null ? Map.of() : result.metadata());
        metadata.put("skillName", request.skillName());
        metadata.put("scriptName", request.scriptName());
        metadata.put("runtime", request.runtime());
        metadata.put("success", result.success());
        metadata.put("exitCode", result.exitCode() == null ? 0 : result.exitCode());
        metadata.put("stdout", text(result.stdout()));
        metadata.put("stderr", text(result.stderr()));
        metadata.put("errorMessage", text(result.errorMessage()));
        metadata.put("argumentKeys", request.arguments().keySet().stream().toList());
        metadata.put("argv", request.argv());

        String summary = result.success()
                ? firstPresent(result.summary(), result.stdout(), "script executed")
                : firstPresent(result.summary(), result.stderr(), result.errorMessage(), "script execution failed");
        String content = firstPresent(result.stdout(), result.stderr(), result.errorMessage());

        return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.SCRIPT_RUNNER)
                .title(request.skillName() + "/" + request.scriptName())
                .summary(limit(summary))
                .content(content)
                .metadata(metadata)
                .fileRefs(fileRefs(result.fileRefs()))
                .build();
    }

    private AcademicScriptRunnerPort.AcademicScriptRunRequest validatedRequest(Map<String, Object> arguments) {
        String runtime = validateRuntime(defaultText(arguments.get("runtime"), "python"));
        return new AcademicScriptRunnerPort.AcademicScriptRunRequest(
                validateOptionalText(text(arguments.get("requestId")), "requestId", MAX_NAME_LENGTH),
                validateName(text(arguments.get("skillName")), "skillName"),
                validateOptionalText(text(arguments.get("skillBasePath")), "skillBasePath", MAX_PATH_LENGTH),
                validateName(text(arguments.get("scriptName")), "scriptName"),
                validateScriptPath(text(arguments.get("scriptPath"))),
                runtime,
                objectMap(arguments.get("arguments")),
                validateArgv(arguments.get("argv")),
                Math.min(MAX_TIMEOUT_SECONDS, Math.max(1, integer(arguments.get("timeoutSeconds"), DEFAULT_TIMEOUT_SECONDS))));
    }

    private String validateName(String value, String fieldName) {
        String text = validateOptionalText(value, fieldName, MAX_NAME_LENGTH);
        if (!StringUtils.hasText(text)) {
            throw new AppException("SCRIPT_RUNNER_0003", fieldName + " cannot be blank");
        }
        if (text.contains("/") || text.contains("\\")) {
            throw new AppException("SCRIPT_RUNNER_0003", fieldName + " cannot contain path separator");
        }
        return text;
    }

    private String validateOptionalText(String value, String fieldName, int maxLength) {
        String text = text(value);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.length() > maxLength || containsControlChar(text) || text.indexOf('\0') >= 0) {
            throw new AppException("SCRIPT_RUNNER_0003", fieldName + " is invalid");
        }
        return text;
    }

    private String validateRuntime(String runtime) {
        String normalized = text(runtime).toLowerCase(Locale.ROOT);
        if (!ALLOWED_RUNTIMES.contains(normalized)) {
            throw new AppException("SCRIPT_RUNNER_0004", "script runtime is not allowed");
        }
        return normalized;
    }

    private String validateScriptPath(String scriptPath) {
        String text = validateOptionalText(scriptPath, "scriptPath", MAX_PATH_LENGTH).replace('\\', '/');
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.startsWith("/") || text.matches("^[A-Za-z]:.*")) {
            throw new AppException("SCRIPT_RUNNER_0005", "scriptPath must be relative");
        }
        try {
            Path normalized = Path.of(text).normalize();
            String normalizedText = normalized.toString().replace('\\', '/');
            if (!StringUtils.hasText(normalizedText)
                    || normalized.isAbsolute()
                    || normalizedText.equals("..")
                    || normalizedText.startsWith("../")
                    || normalizedText.contains("/../")) {
                throw new AppException("SCRIPT_RUNNER_0005", "scriptPath escapes skill directory");
            }
            return normalizedText;
        } catch (InvalidPathException e) {
            throw new AppException("SCRIPT_RUNNER_0005", "scriptPath is invalid");
        }
    }

    private List<String> validateArgv(Object value) {
        List<String> argv = stringList(value);
        if (argv.size() > MAX_ARGV_COUNT) {
            throw new AppException("SCRIPT_RUNNER_0006", "too many script arguments");
        }
        for (String item : argv) {
            if (item.length() > MAX_ARGV_ITEM_LENGTH || containsControlChar(item) || item.indexOf('\0') >= 0) {
                throw new AppException("SCRIPT_RUNNER_0006", "script argument is invalid");
            }
        }
        return argv;
    }

    private boolean containsControlChar(String value) {
        return value.chars().anyMatch(ch -> Character.isISOControl(ch) && ch != '\t');
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : Map.of();
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

    private String limit(String value) {
        String text = text(value);
        return text.length() <= 180 ? text : text.substring(0, 180);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}














