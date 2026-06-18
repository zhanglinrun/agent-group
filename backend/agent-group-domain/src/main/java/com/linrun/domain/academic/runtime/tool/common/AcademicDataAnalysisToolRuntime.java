package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicDataAnalysisPort;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.linrun.domain.academic.runtime.tool.common.AcademicToolArguments.integer;
import static com.linrun.domain.academic.runtime.tool.common.AcademicToolArguments.stringList;
import static com.linrun.domain.academic.runtime.tool.common.AcademicToolArguments.text;

public class AcademicDataAnalysisToolRuntime {

    private final AcademicDataAnalysisPort remotePort;

    public AcademicDataAnalysisToolRuntime() {
        this(null);
    }

    public AcademicDataAnalysisToolRuntime(AcademicDataAnalysisPort remotePort) {
        this.remotePort = remotePort;
    }

    public static AcademicToolDefinition definition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.DATA_ANALYSIS)
                .description("Analyze rows or a configured data model and return statistics, insights, and generated files.")
                .category("analysis")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "task", Map.of("type", "string", "description", "Analysis task."),
                                "rows", Map.of("type", "array", "description", "List of row objects."),
                                "columns", Map.of("type", "array", "description", "Optional column names."),
                                "modelCodeList", Map.of("type", "array", "description", "Remote data model codes."),
                                "businessKnowledge", Map.of("type", "string", "description", "Domain knowledge for analysis."),
                                "maxSteps", Map.of("type", "integer", "description", "Maximum remote analysis steps.")),
                        "required", List.of()))
                .requiredArguments(List.of())
                .enabled(true)
                .build();
    }

    public AcademicToolStructuredOutput call(AcademicToolCallCommand command) {
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        String task = text(arguments.get("task"));
        List<Map<String, Object>> rows = rows(arguments.get("rows"));
        List<String> columns = columns(arguments.get("columns"), rows);
        List<String> modelCodeList = stringList(arguments.get("modelCodeList"));
        if (remotePort != null && !modelCodeList.isEmpty()) {
            AcademicDataAnalysisPort.AcademicDataAnalysisResult remoteResult = remotePort.analyze(
                    new AcademicDataAnalysisPort.AcademicDataAnalysisRequest(
                            requestId(command, "data"),
                            task,
                            rows,
                            columns,
                            modelCodeList,
                            text(arguments.get("businessKnowledge")),
                            integer(arguments.get("maxSteps"), 10),
                            false));
            if (remoteResult.success()) {
                return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.DATA_ANALYSIS)
                        .title(StringUtils.hasText(task) ? task : "data analysis")
                        .summary(remoteResult.summary())
                        .content(remoteResult.content())
                        .metadata(remoteResult.metadata())
                        .fileRefs(remoteResult.fileRefs())
                        .build();
            }
            if (rows.isEmpty()) {
                throw new AppException("DATA_ANALYSIS_REMOTE_FAILED", remoteResult.errorMessage());
            }
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("task", task);
        metadata.put("rowCount", rows.size());
        metadata.put("columnCount", columns.size());
        metadata.put("columns", columns);
        metadata.put("missingValues", missingValues(rows, columns));
        metadata.put("numericStats", numericStats(rows, columns));
        metadata.put("sampleRows", rows.stream().limit(5).toList());

        return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.DATA_ANALYSIS)
                .title(StringUtils.hasText(task) ? task : "data analysis")
                .summary("rows=" + rows.size() + ", columns=" + columns.size())
                .metadata(metadata)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> rows(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                rows.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return rows;
    }

    private List<String> columns(Object value, List<Map<String, Object>> rows) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return stringList(list);
        }
        Set<String> columns = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            columns.addAll(row.keySet());
        }
        return new ArrayList<>(columns);
    }

    private Map<String, Integer> missingValues(List<Map<String, Object>> rows, List<String> columns) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        for (String column : columns) {
            int count = 0;
            for (Map<String, Object> row : rows) {
                Object value = row.get(column);
                if (value == null || !StringUtils.hasText(String.valueOf(value))) {
                    count++;
                }
            }
            missing.put(column, count);
        }
        return missing;
    }

    private Map<String, Map<String, Object>> numericStats(List<Map<String, Object>> rows, List<String> columns) {
        Map<String, Map<String, Object>> stats = new LinkedHashMap<>();
        for (String column : columns) {
            List<BigDecimal> values = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                BigDecimal number = decimal(row.get(column));
                if (number != null) {
                    values.add(number);
                }
            }
            if (!values.isEmpty()) {
                stats.put(column, stats(values));
            }
        }
        return stats;
    }

    private Map<String, Object> stats(List<BigDecimal> values) {
        BigDecimal min = values.getFirst();
        BigDecimal max = values.getFirst();
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : values) {
            if (value.compareTo(min) < 0) {
                min = value;
            }
            if (value.compareTo(max) > 0) {
                max = value;
            }
            sum = sum.add(value);
        }
        BigDecimal average = sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", values.size());
        result.put("min", min.stripTrailingZeros().toPlainString());
        result.put("max", max.stripTrailingZeros().toPlainString());
        result.put("avg", average.stripTrailingZeros().toPlainString());
        return result;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String requestId(AcademicToolCallCommand command, String prefix) {
        if (command != null && StringUtils.hasText(command.getRequestId())) {
            return command.getRequestId();
        }
        return "agent-group-" + prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}















