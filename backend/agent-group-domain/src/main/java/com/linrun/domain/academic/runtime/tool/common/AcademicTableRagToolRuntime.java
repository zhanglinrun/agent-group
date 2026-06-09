package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicTableRagPort;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AcademicTableRagToolRuntime {

    private static final int DEFAULT_TOP_K = 5;

    private final AcademicTableRagPort tableRagPort;

    public AcademicTableRagToolRuntime(AcademicTableRagPort tableRagPort) {
        this.tableRagPort = tableRagPort;
    }

    public static AcademicToolDefinition definition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.TABLE_RAG)
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

    public AcademicToolStructuredOutput call(AcademicToolCallCommand command) {
        if (tableRagPort == null) {
            throw new AppException("TABLE_RAG_0001", "table rag port is not configured");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        AcademicTableRagPort.AcademicTableRagRequest request = new AcademicTableRagPort.AcademicTableRagRequest(
                text(arguments.get("requestId")),
                text(arguments.get("query")),
                stringList(arguments.get("modelCodeList")),
                defaultText(arguments.get("recallType"), "only_recall"),
                bool(arguments.get("useVector"), true),
                bool(arguments.get("useElastic"), false),
                Math.max(1, integer(arguments.get("topK"), DEFAULT_TOP_K)));
        AcademicTableRagPort.AcademicTableRagResult result = tableRagPort.recall(request);
        if (result == null) {
            throw new AppException("TABLE_RAG_0002", "table rag returned empty result");
        }
        if (!result.success()) {
            throw new AppException("TABLE_RAG_0003", firstPresent(result.errorMessage(), "table rag failed"));
        }

        List<AcademicTableRagPort.AcademicTableSchemaMatch> matches = result.matches() == null ? List.of() : result.matches();
        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata() == null ? Map.of() : result.metadata());
        metadata.put("requestId", firstPresent(result.requestId(), request.requestId()));
        metadata.put("query", request.query());
        metadata.put("recallType", request.recallType());
        metadata.put("useVector", request.useVector());
        metadata.put("useElastic", request.useElastic());
        metadata.put("matchCount", matches.size());
        metadata.put("matches", matches.stream().map(this::matchMap).toList());

        return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.TABLE_RAG)
                .title(request.query())
                .summary("matched schemas=" + matches.size())
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> matchMap(AcademicTableRagPort.AcademicTableSchemaMatch match) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("modelCode", text(match.modelCode()));
        map.put("score", match.score());
        map.put("schemaList", match.schemaList() == null ? List.of() : match.schemaList());
        return map;
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
        return list.stream().map(this::text).filter(StringUtils::hasText).toList();
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















