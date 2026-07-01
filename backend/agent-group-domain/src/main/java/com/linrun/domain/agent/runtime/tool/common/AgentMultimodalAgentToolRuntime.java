package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolDefinition;
import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.output.AgentToolStructuredOutput;
import com.linrun.domain.agent.runtime.tool.port.AgentMultimodalAnalysisPort;
import com.linrun.types.exception.AppException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.firstPresent;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.stringList;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.text;

public class AgentMultimodalAgentToolRuntime {

    private final AgentMultimodalAnalysisPort multimodalAnalysisPort;

    public AgentMultimodalAgentToolRuntime(AgentMultimodalAnalysisPort multimodalAnalysisPort) {
        this.multimodalAnalysisPort = multimodalAnalysisPort;
    }

    public static AgentToolDefinition definition() {
        return AgentToolDefinition.builder(AgentToolOutputNames.MULTIMODAL_AGENT)
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

    public AgentToolStructuredOutput call(AgentToolCallCommand command) {
        if (multimodalAnalysisPort == null) {
            throw new AppException("MULTIMODAL_0001", "multimodal analysis port is not configured");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        AgentMultimodalAnalysisPort.AgentMultimodalAnalysisRequest request =
                new AgentMultimodalAnalysisPort.AgentMultimodalAnalysisRequest(
                        text(arguments.get("task")),
                        text(arguments.get("text")),
                        stringList(arguments.get("imageUrls")),
                        stringList(arguments.get("fileUrls")));
        AgentMultimodalAnalysisPort.AgentMultimodalAnalysisResult result =
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

        return AgentToolStructuredOutput.builder(AgentToolOutputNames.MULTIMODAL_AGENT)
                .title(request.task())
                .summary(firstPresent(result.summary(), result.content()))
                .content(text(result.content()))
                .metadata(metadata)
                .fileRefs(fileRefs(result.fileRefs()))
                .build();
    }

    private List<AgentToolFileRef> fileRefs(List<AgentToolFileRef> fileRefs) {
        return fileRefs == null ? List.of() : fileRefs;
    }
}















