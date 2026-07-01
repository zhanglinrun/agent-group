package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolDefinition;
import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.output.AgentToolStructuredOutput;
import com.linrun.domain.agent.runtime.tool.port.AgentDeepSearchPort;
import com.linrun.types.exception.AppException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.bool;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.firstPresent;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.integer;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.objectMap;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.stringList;
import static com.linrun.domain.agent.runtime.tool.common.AgentToolArguments.text;

public class AgentDeepSearchToolRuntime {

    private static final int DEFAULT_MAX_RESULTS = 5;

    private final AgentDeepSearchPort deepSearchPort;

    public AgentDeepSearchToolRuntime(AgentDeepSearchPort deepSearchPort) {
        this.deepSearchPort = deepSearchPort;
    }

    public static AgentToolDefinition definition() {
        return AgentToolDefinition.builder(AgentToolOutputNames.DEEP_SEARCH)
                .description("Run deep search through a configurable search port and return answer, sources, and files.")
                .category("search")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "Search query."),
                                "maxResults", Map.of("type", "integer", "description", "Maximum source documents."),
                                "stream", Map.of("type", "boolean", "description", "Whether upstream search may stream."),
                                "sourceTypes", Map.of("type", "array", "description", "Preferred search source types."),
                                "options", Map.of("type", "object", "description", "Provider-specific options.")),
                        "required", List.of("query")))
                .requiredArguments(List.of("query"))
                .enabled(true)
                .build();
    }

    public AgentToolStructuredOutput call(AgentToolCallCommand command) {
        if (deepSearchPort == null) {
            throw new AppException("DEEP_SEARCH_0001", "deep search port is not configured");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        AgentDeepSearchPort.AgentDeepSearchRequest request =
                new AgentDeepSearchPort.AgentDeepSearchRequest(
                        text(arguments.get("query")),
                        bounded(integer(arguments.get("maxResults"), DEFAULT_MAX_RESULTS), 1, 50),
                        bool(arguments.get("stream"), true),
                        stringList(arguments.get("sourceTypes")),
                        objectMap(arguments.get("options")));
        AgentDeepSearchPort.AgentDeepSearchResult result = deepSearchPort.search(request);
        if (result == null) {
            throw new AppException("DEEP_SEARCH_0002", "deep search returned empty result");
        }
        if (!result.success()) {
            throw new AppException("DEEP_SEARCH_0003", firstPresent(result.errorMessage(), "deep search failed"));
        }

        List<AgentDeepSearchPort.AgentDeepSearchDocument> documents = documents(result.documents());
        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata() == null ? Map.of() : result.metadata());
        metadata.put("query", firstPresent(result.query(), request.query()));
        metadata.put("maxResults", request.maxResults());
        metadata.put("stream", request.stream());
        metadata.put("sourceTypes", request.sourceTypes());
        metadata.put("subQueries", stringList(result.subQueries()));
        metadata.put("documentCount", documents.size());
        metadata.put("documents", documents.stream().map(this::docMap).toList());

        return AgentToolStructuredOutput.builder(AgentToolOutputNames.DEEP_SEARCH)
                .title(firstPresent(result.query(), request.query()))
                .summary(limit(firstPresent(result.answerSummary(), result.answer())))
                .content(text(result.answer()))
                .metadata(metadata)
                .fileRefs(fileRefs(result.fileRefs()))
                .build();
    }

    private Map<String, Object> docMap(AgentDeepSearchPort.AgentDeepSearchDocument doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "title", doc.title());
        putIfPresent(map, "url", doc.url());
        putIfPresent(map, "content", limit(doc.content()));
        putIfPresent(map, "source", doc.source());
        return map;
    }

    private List<AgentDeepSearchPort.AgentDeepSearchDocument> documents(
            List<AgentDeepSearchPort.AgentDeepSearchDocument> documents) {
        return documents == null ? List.of() : documents;
    }

    private List<AgentToolFileRef> fileRefs(List<AgentToolFileRef> fileRefs) {
        return fileRefs == null ? List.of() : fileRefs;
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        String text = firstPresent(value);
        if (!text.isEmpty()) {
            map.put(key, text);
        }
    }

    private int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String limit(String value) {
        String text = text(value);
        return text.length() <= 240 ? text : text.substring(0, 240);
    }
}















