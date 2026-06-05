package com.linrun.domain.academic.runtime.tool.output;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AcademicToolOutputProjector {

    private AcademicToolOutputProjector() {
    }

    public static Map<String, Object> toResultMap(AcademicToolStructuredOutput output) {
        if (output == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toolName", output.getToolName());
        putIfPresent(result, "title", output.getTitle());
        putIfPresent(result, "summary", output.getSummary());
        putIfPresent(result, "content", output.getContent());
        result.put("metadata", output.getMetadata());
        result.put("fileRefs", output.getFileRefs().stream()
                .map(AcademicToolFileRef::toMap)
                .toList());
        return result;
    }

    public static AcademicToolCallResult toCallResult(AcademicToolStructuredOutput output,
                                                      String action,
                                                      long latencyMillis) {
        Map<String, Object> result = toResultMap(output);
        return AcademicToolCallResult.success(
                        output == null ? "" : output.getToolName(), action, result, latencyMillis)
                .artifactIds(extractArtifactIds(result))
                .build();
    }

    @SuppressWarnings("unchecked")
    public static List<String> extractArtifactIds(Map<String, Object> result) {
        if (result == null) {
            return List.of();
        }
        Object fileRefs = result.get("fileRefs");
        if (!(fileRefs instanceof List<?> refs)) {
            return List.of();
        }
        return refs.stream()
                .filter(Map.class::isInstance)
                .map(ref -> (Map<String, Object>) ref)
                .map(ref -> String.valueOf(ref.getOrDefault("artifactId", "")))
                .filter(StringUtils::hasText)
                .toList();
    }

    public static String summarize(AcademicToolStructuredOutput output) {
        if (output == null) {
            return "";
        }
        if (StringUtils.hasText(output.getSummary())) {
            return limit(output.getSummary());
        }
        if (StringUtils.hasText(output.getContent())) {
            return limit(output.getContent());
        }
        return limit(output.getTitle());
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value);
        }
    }

    private static String limit(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 180 ? text : text.substring(0, 180);
    }
}
