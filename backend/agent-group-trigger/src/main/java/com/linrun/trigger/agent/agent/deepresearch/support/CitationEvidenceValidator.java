package com.linrun.trigger.agent.agent.deepresearch.support;

import com.linrun.trigger.agent.entity.record.SearchResult;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class CitationEvidenceValidator {

    private static final Pattern TASK_CITATION = Pattern.compile(
            "\\btask-\\d+\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOI_PATTERN = Pattern.compile(
            "\\b10\\.\\d{4,9}/[-._;()/:A-Z0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern PERCENT_PATTERN = Pattern.compile(
            "\\b\\d{1,3}(?:\\.\\d+)?\\s*%");
    private static final Pattern ACCURACY_PATTERN = Pattern.compile(
            "\\b\\d{1,3}(?:\\.\\d+)?\\s*%\\s*(?:准确|accuracy)", Pattern.CASE_INSENSITIVE);

    private CitationEvidenceValidator() {
    }

    public record ValidationResult(
            boolean passed,
            List<String> warnings
    ) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failed(List<String> warnings) {
            return new ValidationResult(false, warnings == null ? List.of() : List.copyOf(warnings));
        }
    }

    public static ValidationResult validateSummary(String summary, List<SearchResult> references) {
        if (!StringUtils.hasText(summary)) {
            return ValidationResult.ok();
        }
        List<String> warnings = new ArrayList<>();
        if (TASK_CITATION.matcher(summary).find()) {
            warnings.add("最终回答使用了 task-N 内部步骤编号作为引用，请改为 url/title 或标记为待验证。");
        }
        boolean hasReferences = references != null && references.stream()
                .anyMatch(reference -> reference != null && StringUtils.hasText(reference.url()));
        if (!hasReferences) {
            if (DOI_PATTERN.matcher(summary).find()) {
                warnings.add("未检索到可验证来源，但回答包含 DOI，请删除或改为待验证。");
            }
            if (ACCURACY_PATTERN.matcher(summary).find() || PERCENT_PATTERN.matcher(summary).find()) {
                warnings.add("未检索到可验证来源，但回答包含百分比/准确率，请删除或改为待验证。");
            }
        }
        if (warnings.isEmpty()) {
            return ValidationResult.ok();
        }
        return ValidationResult.failed(warnings);
    }

    public static ValidationResult validateToolOutputs(String combinedOutputs, List<SearchResult> references) {
        if (!StringUtils.hasText(combinedOutputs)) {
            return ValidationResult.failed(List.of("当前轮次没有可用的工具结果，不应进入最终总结。"));
        }
        List<String> warnings = new ArrayList<>();
        if (TASK_CITATION.matcher(combinedOutputs).find()) {
            warnings.add("工具结果中混入了 task-N 引用格式，请重新检索并整理真实来源。");
        }
        boolean hasReferences = references != null && !references.isEmpty();
        if (!hasReferences && containsStrongClaim(combinedOutputs)) {
            warnings.add("工具结果缺少 url 来源，但出现了 DOI/准确率等强断言，请补充检索。");
        }
        if (warnings.isEmpty()) {
            return ValidationResult.ok();
        }
        return ValidationResult.failed(warnings);
    }

    public static String formatReferenceAllowList(List<SearchResult> references) {
        if (references == null || references.isEmpty()) {
            return "（当前无可引用 url，不得输出确定事实、DOI、准确率数字）";
        }
        Set<String> lines = new LinkedHashSet<>();
        for (SearchResult reference : references) {
            if (reference == null || !StringUtils.hasText(reference.url())) {
                continue;
            }
            String title = StringUtils.hasText(reference.title()) ? reference.title().trim() : "untitled";
            lines.add("- url: " + reference.url().trim() + "\n  title: " + title);
        }
        if (lines.isEmpty()) {
            return "（当前无可引用 url，不得输出确定事实、DOI、准确率数字）";
        }
        return String.join("\n", lines);
    }

    public static String formatEvidenceSources(List<SearchResult> sources) {
        if (sources == null || sources.isEmpty()) {
            return "sources: []\n";
        }
        StringBuilder builder = new StringBuilder("sources:\n");
        for (SearchResult source : sources) {
            if (source == null) {
                continue;
            }
            builder.append("- url: ").append(nullToEmpty(source.url())).append('\n');
            builder.append("  title: ").append(nullToEmpty(source.title())).append('\n');
            if (StringUtils.hasText(source.content())) {
                String snippet = source.content().trim();
                builder.append("  snippet: ")
                        .append(snippet.length() <= 300 ? snippet : snippet.substring(0, 300) + "...")
                        .append('\n');
            }
        }
        return builder.toString();
    }

    private static boolean containsStrongClaim(String text) {
        return DOI_PATTERN.matcher(text).find() || ACCURACY_PATTERN.matcher(text).find();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
