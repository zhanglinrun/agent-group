package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolDefinition;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.output.AgentToolStructuredOutput;
import com.linrun.domain.agent.runtime.tool.port.AgentTableRagPort;
import com.linrun.types.exception.AppException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.bool;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.defaultText;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.firstPresent;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.integer;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.stringList;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.text;

public class AgentTableRagToolRuntime {

    private static final int DEFAULT_TOP_K = 5;

    private final AgentTableRagPort tableRagPort;

    public AgentTableRagToolRuntime(AgentTableRagPort tableRagPort) {
        this.tableRagPort = tableRagPort;
    }

    public static AgentToolDefinition definition() {
        return AgentToolDefinition.builder(AgentToolOutputNames.TABLE_RAG)
                .description("Recall table and column schemas for data questions through a configurable table RAG port.")
                .category("data")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "requestId", Map.of("type", "string", "description", "Request id."),
                                "query", Map.of("type", "string", "description", "User data question."),
                                "modelCodeList", Map.of("type", "array", "description", "Candidate table model ids."),
                                "recallType", Map.of("type", "string", "description", "Recall mode."),
                                "useVector", Map.of("type", "boolean", "description", "Whether to use vector recall."),
                                "useElastic", Map.of("type", "boolean", "description", "Whether to use keyword recall."),
                                "topK", Map.of("type", "integer", "description", "Maximum schema matches.")),
                        "required", List.of("query")))
                .requiredArguments(List.of("query"))
                .enabled(true)
                .build();
    }

    public AgentToolStructuredOutput call(AgentToolCallCommand command) {
        if (tableRagPort == null) {
            throw new AppException("TABLE_RAG_0001", "table rag port is not configured");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        AgentTableRagPort.AgentTableRagRequest request = new AgentTableRagPort.AgentTableRagRequest(
                text(arguments.get("requestId")),
                text(arguments.get("query")),
                stringList(arguments.get("modelCodeList")),
                defaultText(arguments.get("recallType"), "only_recall"),
                bool(arguments.get("useVector"), true),
                bool(arguments.get("useElastic"), false),
                Math.max(1, integer(arguments.get("topK"), DEFAULT_TOP_K)));
        AgentTableRagPort.AgentTableRagResult result = tableRagPort.recall(request);
        if (result == null) {
            throw new AppException("TABLE_RAG_0002", "table rag returned empty result");
        }
        if (!result.success()) {
            throw new AppException("TABLE_RAG_0003", firstPresent(result.errorMessage(), "table rag failed"));
        }

        List<AgentTableRagPort.AgentTableSchemaMatch> matches = result.matches() == null ? List.of() : result.matches();
        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata() == null ? Map.of() : result.metadata());
        metadata.put("requestId", firstPresent(result.requestId(), request.requestId()));
        metadata.put("query", request.query());
        metadata.put("recallType", request.recallType());
        metadata.put("useVector", request.useVector());
        metadata.put("useElastic", request.useElastic());
        metadata.put("matchCount", matches.size());
        metadata.put("matches", matches.stream().map(this::matchMap).toList());

        return AgentToolStructuredOutput.builder(AgentToolOutputNames.TABLE_RAG)
                .title(request.query())
                .summary("matched schemas=" + matches.size())
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> matchMap(AgentTableRagPort.AgentTableSchemaMatch match) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("modelCode", text(match.modelCode()));
        map.put("score", match.score());
        map.put("schemaList", match.schemaList() == null ? List.of() : match.schemaList());
        return map;
    }
}















