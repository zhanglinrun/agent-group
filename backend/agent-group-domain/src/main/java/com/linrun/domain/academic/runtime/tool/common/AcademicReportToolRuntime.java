package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolDefinition;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolStructuredOutput;
import com.linrun.domain.academic.runtime.tool.port.AcademicReportPort;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AcademicReportToolRuntime {

    private final AcademicReportPort remotePort;

    public AcademicReportToolRuntime() {
        this(null);
    }

    public AcademicReportToolRuntime(AcademicReportPort remotePort) {
        this.remotePort = remotePort;
    }

    public static AcademicToolDefinition definition() {
        return AcademicToolDefinition.builder(AcademicToolOutputNames.REPORT_TOOL)
                .description("Build a markdown/html report from title, summary, sections, evidence, and files.")
                .category("report")
                .source("local")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "task", Map.of("type", "string", "description", "Report generation task."),
                                "title", Map.of("type", "string", "description", "Report title."),
                                "summary", Map.of("type", "string", "description", "Executive summary."),
                                "sections", Map.of("type", "array", "description", "Report sections."),
                                "evidence", Map.of("type", "array", "description", "Evidence items."),
                                "fileNames", Map.of("type", "array", "description", "Input files for remote report generation."),
                                "fileName", Map.of("type", "string", "description", "Generated report file name."),
                                "fileType", Map.of("type", "string", "description", "markdown, html, or ppt."),
                                "templateType", Map.of("type", "string", "description", "Remote report template.")),
                        "required", List.of()))
                .requiredArguments(List.of())
                .enabled(true)
                .build();
    }

    public AcademicToolStructuredOutput call(AcademicToolCallCommand command) {
        Map<String, Object> arguments = command == null ? Map.of() : command.getArguments();
        String task = text(arguments.get("task"));
        String title = firstText(arguments.get("title"), task);
        String summary = text(arguments.get("summary"));
        List<ReportSection> sections = sections(arguments.get("sections"));
        List<String> evidence = strings(arguments.get("evidence"));
        if (remotePort != null && shouldUseRemote(arguments)) {
            AcademicReportPort.AcademicReportResult remoteResult = remotePort.generate(
                    new AcademicReportPort.AcademicReportRequest(
                            requestId(command, "report"),
                            taskText(task, title, summary, sections, evidence),
                            title,
                            summary,
                            sectionMaps(arguments.get("sections"), sections),
                            evidence,
                            strings(arguments.get("fileNames")),
                            firstText(arguments.get("fileName"), defaultFileName(title)),
                            firstText(arguments.get("fileType"), "markdown"),
                            firstText(arguments.get("templateType"), "html"),
                            false));
            if (remoteResult.success()) {
                return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.REPORT_TOOL)
                        .title(title)
                        .summary(remoteResult.summary())
                        .content(remoteResult.content())
                        .metadata(remoteResult.metadata())
                        .fileRefs(remoteResult.fileRefs())
                        .build();
            }
            if (!StringUtils.hasText(title) && sections.isEmpty()) {
                throw new AppException("REPORT_REMOTE_FAILED", remoteResult.errorMessage());
            }
        }
        String content = markdown(title, summary, sections, evidence);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("format", "markdown");
        metadata.put("sectionCount", sections.size());
        metadata.put("evidenceCount", evidence.size());
        metadata.put("generatedAt", LocalDateTime.now().toString());

        return AcademicToolStructuredOutput.builder(AcademicToolOutputNames.REPORT_TOOL)
                .title(title)
                .summary(StringUtils.hasText(summary) ? summary : firstSectionSummary(sections))
                .content(content)
                .metadata(metadata)
                .build();
    }

    private boolean shouldUseRemote(Map<String, Object> arguments) {
        return StringUtils.hasText(text(arguments.get("task")))
                || StringUtils.hasText(text(arguments.get("fileName")))
                || !strings(arguments.get("fileNames")).isEmpty();
    }

    private String markdown(String title,
                            String summary,
                            List<ReportSection> sections,
                            List<String> evidence) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(title).append("\n\n");
        if (StringUtils.hasText(summary)) {
            markdown.append("## Summary\n\n").append(summary).append("\n\n");
        }
        for (ReportSection section : sections) {
            markdown.append("## ").append(section.heading()).append("\n\n");
            markdown.append(section.content()).append("\n\n");
        }
        if (!evidence.isEmpty()) {
            markdown.append("## Evidence\n\n");
            for (String item : evidence) {
                markdown.append("- ").append(item).append("\n");
            }
        }
        return markdown.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private List<ReportSection> sections(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<ReportSection> sections = new ArrayList<>();
        int index = 1;
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String heading = text(((Map<String, Object>) map).get("heading"));
                if (!StringUtils.hasText(heading)) {
                    heading = text(((Map<String, Object>) map).get("title"));
                }
                String content = text(((Map<String, Object>) map).get("content"));
                if (StringUtils.hasText(heading) || StringUtils.hasText(content)) {
                    sections.add(new ReportSection(
                            StringUtils.hasText(heading) ? heading : "Section " + index,
                            content));
                    index++;
                }
            } else {
                String content = text(item);
                if (StringUtils.hasText(content)) {
                    sections.add(new ReportSection("Section " + index, content));
                    index++;
                }
            }
        }
        return sections;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sectionMaps(Object value, List<ReportSection> fallback) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return fallback.stream()
                .map(section -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("heading", section.heading());
                    map.put("content", section.content());
                    return map;
                })
                .toList();
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(this::text)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String taskText(String task,
                            String title,
                            String summary,
                            List<ReportSection> sections,
                            List<String> evidence) {
        if (StringUtils.hasText(task)) {
            return task;
        }
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(title)) {
            builder.append("标题：").append(title).append("\n");
        }
        if (StringUtils.hasText(summary)) {
            builder.append("摘要：").append(summary).append("\n");
        }
        for (ReportSection section : sections) {
            builder.append(section.heading()).append("：").append(section.content()).append("\n");
        }
        if (!evidence.isEmpty()) {
            builder.append("依据：").append(String.join("；", evidence));
        }
        return builder.toString().trim();
    }

    private String firstSectionSummary(List<ReportSection> sections) {
        if (sections.isEmpty()) {
            return "";
        }
        String content = sections.getFirst().content();
        return content.length() <= 160 ? content : content.substring(0, 160);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String defaultFileName(String title) {
        String safeTitle = StringUtils.hasText(title) ? title.replaceAll("[\\\\/:*?\"<>|\\s]+", "_") : "report";
        return safeTitle + ".md";
    }

    private String requestId(AcademicToolCallCommand command, String prefix) {
        if (command != null && StringUtils.hasText(command.getRequestId())) {
            return command.getRequestId();
        }
        return "agent-group-" + prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private record ReportSection(String heading, String content) {
    }
}















