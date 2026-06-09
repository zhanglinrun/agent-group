package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicDeepSearchPort;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AcademicDeepSearchToolRuntime {

    private static final int DEFAULT_MAX_RESULTS = 5;

    private final AcademicDeepSearchPort deepSearchPort;

    public AcademicDeepSearchToolRuntime(AcademicDeepSearchPort deepSearchPort) {
        this.deepSearchPort = deepSearchPort;
    }

    public static AcademicToolDefinition definition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.DEEP_SEARCH)
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

    public AcademicToolStructuredOutput call(AcademicToolCallCommand command) {
        if (deepSearchPort == null) {
            throw new AppException("DEEP_SEARCH_0001", "deep search port is not configured");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        AcademicDeepSearchPort.AcademicDeepSearchRequest request =
                new AcademicDeepSearchPort.AcademicDeepSearchRequest(
                        text(arguments.get("query")),
                        bounded(integer(arguments.get("maxResults"), DEFAULT_MAX_RESULTS), 1, 50),
                        bool(arguments.get("stream"), true),
                        stringList(arguments.get("sourceTypes")),
                        objectMap(arguments.get("options")));
        AcademicDeepSearchPort.AcademicDeepSearchResult result = deepSearchPort.search(request);
        if (result == null) {
            throw new AppException("DEEP_SEARCH_0002", "deep search returned empty result");
        }
        if (!result.success()) {
            throw new AppException("DEEP_SEARCH_0003", firstPresent(result.errorMessage(), "deep search failed"));
        }

        List<AcademicDeepSearchPort.AcademicDeepSearchDocument> documents = documents(result.documents());
        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata() == null ? Map.of() : result.metadata());
        metadata.put("query", firstPresent(result.query(), request.query()));
        metadata.put("maxResults", request.maxResults());
        metadata.put("stream", request.stream());
        metadata.put("sourceTypes", request.sourceTypes());
        metadata.put("subQueries", stringList(result.subQueries()));
        metadata.put("documentCount", documents.size());
        metadata.put("documents", documents.stream().map(this::docMap).toList());

        return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.DEEP_SEARCH)
                .title(firstPresent(result.query(), request.query()))
                .summary(limit(firstPresent(result.answerSummary(), result.answer())))
                .content(text(result.answer()))
                .metadata(metadata)
                .fileRefs(fileRefs(result.fileRefs()))
                .build();
    }

    private Map<String, Object> docMap(AcademicDeepSearchPort.AcademicDeepSearchDocument doc) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "title", doc.title());
        putIfPresent(map, "url", doc.url());
        putIfPresent(map, "content", limit(doc.content()));
        putIfPresent(map, "source", doc.source());
        return map;
    }

    private List<AcademicDeepSearchPort.AcademicDeepSearchDocument> documents(
            List<AcademicDeepSearchPort.AcademicDeepSearchDocument> documents) {
        return documents == null ? List.of() : documents;
    }

    private List<AcademicToolFileRef> fileRefs(List<AcademicToolFileRef> fileRefs) {
        return fileRefs == null ? List.of() : fileRefs;
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value.trim());
        }
    }

    private int bounded(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
        return text.length() <= 240 ? text : text.substring(0, 240);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}















