package com.linrun.domain.academic.runtime.tool.output;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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

    public static List<String> extractArtifactIds(Map<String, Object> result) {
        if (result == null) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        collectArtifactIds(result, ids);
        return new ArrayList<>(ids);
    }

    public static boolean hasArtifactReferences(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return false;
        }
        return !extractArtifactIds(result).isEmpty() || hasReferencePayload(result);
    }

    @SuppressWarnings("unchecked")
    private static void collectArtifactIds(Map<String, Object> value, LinkedHashSet<String> ids) {
        addArtifactId(value, ids);
        collectArtifactIds(value.get("fileRefs"), ids);
        collectArtifactIds(value.get("artifactRefs"), ids);
        collectArtifactIds(value.get("fileInfo"), ids);
        collectArtifactIds(value.get("fileList"), ids);
        for (String key : List.of("result", "resultMap", "structuredOutput")) {
            Object nested = value.get(key);
            if (nested instanceof Map<?, ?> map) {
                collectArtifactIds((Map<String, Object>) map, ids);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void collectArtifactIds(Object value, LinkedHashSet<String> ids) {
        if (value instanceof Map<?, ?> map) {
            collectArtifactIds((Map<String, Object>) map, ids);
            return;
        }
        if (!(value instanceof List<?> refs)) {
            return;
        }
        for (Object ref : refs) {
            if (ref instanceof Map<?, ?> map) {
                collectArtifactIds((Map<String, Object>) map, ids);
            }
        }
    }

    private static void addArtifactId(Map<String, Object> value, LinkedHashSet<String> ids) {
        String artifactId = firstText(value.get("artifactId"), value.get("fileId"), value.get("resourceKey"));
        if (StringUtils.hasText(artifactId)) {
            ids.add(artifactId);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean hasReferencePayload(Map<String, Object> value) {
        for (String key : List.of("fileRefs", "artifactRefs", "fileInfo", "fileList")) {
            if (hasReferencePayload(value.get(key))) {
                return true;
            }
        }
        if (hasPrimaryFilePayload(value)) {
            return true;
        }
        for (String key : List.of("result", "resultMap", "structuredOutput")) {
            Object nested = value.get(key);
            if (nested instanceof Map<?, ?> map && hasReferencePayload((Map<String, Object>) map)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean hasReferencePayload(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> ref = (Map<String, Object>) map;
            return StringUtils.hasText(firstText(ref.get("artifactId"), ref.get("fileId"), ref.get("resourceKey")))
                    || hasPrimaryFilePayload(ref)
                    || hasReferencePayload(ref);
        }
        if (!(value instanceof List<?> refs)) {
            return false;
        }
        for (Object ref : refs) {
            if (hasReferencePayload(ref)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasPrimaryFilePayload(Map<String, Object> value) {
        return StringUtils.hasText(firstText(
                value.get("primaryFileName"),
                value.get("fileName"),
                value.get("filename"),
                value.get("displayName"),
                value.get("name")))
                || StringUtils.hasText(firstText(
                value.get("downloadUrl"),
                value.get("ossUrl"),
                value.get("domainUrl"),
                value.get("url"),
                value.get("previewUrl")));
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

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = value == null ? "" : String.valueOf(value).trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private static String limit(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 180 ? text : text.substring(0, 180);
    }
}















