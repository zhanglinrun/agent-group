package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicNl2SqlPort;
import com.linrun.types.exception.AppException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.linrun.domain.academic.runtime.tool.common.AcademicToolArguments.bool;
import static com.linrun.domain.academic.runtime.tool.common.AcademicToolArguments.defaultText;
import static com.linrun.domain.academic.runtime.tool.common.AcademicToolArguments.firstPresent;
import static com.linrun.domain.academic.runtime.tool.common.AcademicToolArguments.mapList;
import static com.linrun.domain.academic.runtime.tool.common.AcademicToolArguments.stringList;
import static com.linrun.domain.academic.runtime.tool.common.AcademicToolArguments.text;

public class AcademicNl2SqlToolRuntime {

    private final AcademicNl2SqlPort nl2SqlPort;

    public AcademicNl2SqlToolRuntime(AcademicNl2SqlPort nl2SqlPort) {
        this.nl2SqlPort = nl2SqlPort;
    }

    public static AcademicToolDefinition definition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.NL2SQL)
                .description("Convert natural language data questions into SQL through a configurable NL2SQL port.")
                .category("data")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "requestId", Map.of("type", "string", "description", "Request id."),
                                "query", Map.of("type", "string", "description", "User data question."),
                                "modelCodeList", Map.of("type", "array", "description", "Candidate table model ids."),
                                "schemaInfo", Map.of("type", "array", "description", "Table and column schema info."),
                                "currentDateInfo", Map.of("type", "string", "description", "Current date context."),
                                "dbType", Map.of("type", "string", "description", "SQL dialect."),
                                "stream", Map.of("type", "boolean", "description", "Whether upstream may stream."),
                                "useVector", Map.of("type", "boolean", "description", "Whether to use vector schema recall."),
                                "useElastic", Map.of("type", "boolean", "description", "Whether to use keyword schema recall.")),
                        "required", List.of("query")))
                .requiredArguments(List.of("query"))
                .enabled(true)
                .build();
    }

    public AcademicToolStructuredOutput call(AcademicToolCallCommand command) {
        if (nl2SqlPort == null) {
            throw new AppException("NL2SQL_0001", "nl2sql port is not configured");
        }
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        AcademicNl2SqlPort.AcademicNl2SqlRequest request = new AcademicNl2SqlPort.AcademicNl2SqlRequest(
                text(arguments.get("requestId")),
                text(arguments.get("query")),
                stringList(arguments.get("modelCodeList")),
                mapList(arguments.get("schemaInfo")),
                defaultText(arguments.get("currentDateInfo"), "current date: " + LocalDate.now()),
                defaultText(arguments.get("dbType"), "mysql"),
                bool(arguments.get("stream"), true),
                bool(arguments.get("useVector"), true),
                bool(arguments.get("useElastic"), false));
        AcademicNl2SqlPort.AcademicNl2SqlResult result = nl2SqlPort.convert(request);
        if (result == null) {
            throw new AppException("NL2SQL_0002", "nl2sql returned empty result");
        }
        if (!result.success()) {
            throw new AppException("NL2SQL_0003", firstPresent(result.errorMessage(), "nl2sql failed"));
        }

        List<AcademicNl2SqlPort.AcademicSqlCandidate> candidates = result.candidates() == null ? List.of() : result.candidates();
        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata() == null ? Map.of() : result.metadata());
        metadata.put("requestId", firstPresent(result.requestId(), request.requestId()));
        metadata.put("query", request.query());
        metadata.put("rootQuery", text(result.rootQuery()));
        metadata.put("think", text(result.think()));
        metadata.put("status", text(result.status()));
        metadata.put("dbType", request.dbType());
        metadata.put("candidateCount", candidates.size());
        metadata.put("candidates", candidates.stream().map(this::candidateMap).toList());

        return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.NL2SQL)
                .title(request.query())
                .summary(firstPresent(firstSql(candidates), result.status(), "sql generated"))
                .content(content(candidates))
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> candidateMap(AcademicNl2SqlPort.AcademicSqlCandidate candidate) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("query", text(candidate.query()));
        map.put("sql", text(candidate.sql()));
        return map;
    }

    private String firstSql(List<AcademicNl2SqlPort.AcademicSqlCandidate> candidates) {
        return candidates.isEmpty() ? "" : text(candidates.getFirst().sql());
    }

    private String content(List<AcademicNl2SqlPort.AcademicSqlCandidate> candidates) {
        return String.join("\n\n", candidates.stream()
                .map(candidate -> text(candidate.query()) + "\n" + text(candidate.sql()))
                .toList());
    }
}















