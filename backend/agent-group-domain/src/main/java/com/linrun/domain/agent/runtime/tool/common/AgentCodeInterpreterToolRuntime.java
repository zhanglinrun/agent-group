package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolDefinition;
import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.output.AgentToolStructuredOutput;
import com.linrun.domain.agent.runtime.tool.port.AgentCodeInterpreterPort;
import com.linrun.types.exception.AppException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.defaultText;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.firstPresent;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.stringList;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.text;

public class AgentCodeInterpreterToolRuntime {

    private final AgentCodeInterpreterPort codeInterpreterPort;

    public AgentCodeInterpreterToolRuntime(AgentCodeInterpreterPort codeInterpreterPort) {
        this.codeInterpreterPort = codeInterpreterPort;
    }

    public static AgentToolDefinition definition() {
        return AgentToolDefinition.builder(AgentToolOutputNames.CODE_INTERPRETER)
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
                                        "enum", AgentCodeInterpreterPort.allowedPermissionProfiles(),
                                        "default", AgentCodeInterpreterPort.PERMISSION_PROFILE_ANALYSIS,
                                        "description", "Permission profile: analysis or workspace.")),
                        "required", List.of("task")))
                .requiredArguments(List.of("task"))
                .enabled(true)
                .build();
    }

    public AgentToolStructuredOutput call(AgentToolCallCommand command) {
        if (codeInterpreterPort == null) {
            throw new AppException("CODE_0001", "code interpreter port is not configured");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        AgentCodeInterpreterPort.AgentCodeExecutionRequest request =
                new AgentCodeInterpreterPort.AgentCodeExecutionRequest(
                        text(arguments.get("task")),
                        defaultText(arguments.get("language"), "python"),
                        text(arguments.get("code")),
                        stringList(arguments.get("fileNames")),
                        permissionProfile(arguments.get("permissionProfile")));
        AgentCodeInterpreterPort.AgentCodeExecutionResult result = codeInterpreterPort.execute(request);
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
        return AgentToolStructuredOutput.builder(AgentToolOutputNames.CODE_INTERPRETER)
                .title(request.task())
                .summary(limit(summary))
                .content(text(result.content()))
                .metadata(metadata)
                .fileRefs(fileRefs(result.fileRefs()))
                .build();
    }

    private List<AgentToolFileRef> fileRefs(List<AgentToolFileRef> fileRefs) {
        return fileRefs == null ? List.of() : fileRefs;
    }

    private String permissionProfile(Object value) {
        try {
            return AgentCodeInterpreterPort.normalizePermissionProfile(text(value));
        } catch (IllegalArgumentException e) {
            throw new AppException("CODE_0003", "unsupported code interpreter permission profile");
        }
    }

    private String limit(String value) {
        String text = text(value);
        return text.length() <= 180 ? text : text.substring(0, 180);
    }
}















