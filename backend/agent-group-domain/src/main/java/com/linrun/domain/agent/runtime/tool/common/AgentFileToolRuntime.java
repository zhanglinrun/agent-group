package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolDefinition;
import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.output.AgentToolStructuredOutput;
import com.linrun.domain.agent.runtime.tool.port.AgentFileToolPort;
import com.linrun.types.exception.AppException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.bool;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.defaultText;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.firstPresent;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.integer;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.text;

public class AgentFileToolRuntime {

    private static final int DEFAULT_MAX_CONTENT_CHARS = 4000;

    private final AgentFileToolPort fileToolPort;

    public AgentFileToolRuntime(AgentFileToolPort fileToolPort) {
        this.fileToolPort = fileToolPort;
    }

    public static AgentToolDefinition definition() {
        return AgentToolDefinition.builder(AgentToolOutputNames.FILE_TOOL)
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

    public AgentToolStructuredOutput call(AgentToolCallCommand command) {
        if (fileToolPort == null) {
            throw new AppException("FILE_TOOL_0001", "file tool port is not configured");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        String action = text(arguments.get("command")).toLowerCase(Locale.ROOT);
        AgentFileToolPort.AgentFileToolResult result;
        if ("upload".equals(action)) {
            result = fileToolPort.upload(new AgentFileToolPort.AgentFileUploadRequest(
                    text(arguments.get("requestId")),
                    text(arguments.get("fileName")),
                    text(arguments.get("description")),
                    text(arguments.get("content")),
                    defaultText(arguments.get("contentType"), "text/markdown"),
                    bool(arguments.get("internalFile"), false)));
        } else if ("get".equals(action) || "read".equals(action)) {
            result = fileToolPort.get(new AgentFileToolPort.AgentFileGetRequest(
                    text(arguments.get("requestId")),
                    text(arguments.get("fileName")),
                    Math.max(256, integer(arguments.get("maxContentChars"), DEFAULT_MAX_CONTENT_CHARS))));
            action = "get";
        } else {
            throw new AppException("FILE_TOOL_0002", "unsupported file command: " + action);
        }
        return project(action, arguments, result);
    }

    private AgentToolStructuredOutput project(String action,
                                                 Map<String, Object> arguments,
                                                 AgentFileToolPort.AgentFileToolResult result) {
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

        return AgentToolStructuredOutput.builder(AgentToolOutputNames.FILE_TOOL)
                .title(firstPresent(result.command(), action) + " " + fileName)
                .summary(firstPresent(result.summary(), fileName))
                .content(text(result.content()))
                .metadata(metadata)
                .fileRefs(fileRefs(result.fileRefs()))
                .build();
    }

    private List<AgentToolFileRef> fileRefs(List<AgentToolFileRef> fileRefs) {
        return fileRefs == null ? List.of() : fileRefs;
    }
}















