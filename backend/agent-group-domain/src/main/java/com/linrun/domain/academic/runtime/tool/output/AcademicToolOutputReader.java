package com.linrun.domain.academic.runtime.tool.output;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.model.AcademicToolInvocation;
import com.linrun.domain.academic.model.AcademicArtifact;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AcademicToolOutputReader {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public AcademicToolOutputReader() {
        this(new ObjectMapper());
    }

    public AcademicToolOutputReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public AcademicToolOutputView read(AcademicToolInvocation invocation, List<AcademicArtifact> artifacts) {
        if (invocation == null) {
            return AcademicToolOutputView.builder("").build();
        }
        Map<String, Object> structuredOutput = structuredOutput(invocation);
        List<AcademicToolFileRef> fileRefs = fileRefs(structuredOutput);
        List<AcademicArtifact> artifactRefs = artifactRefs(invocation, artifacts, fileRefs);
        if (fileRefs.isEmpty() && !artifactRefs.isEmpty()) {
            fileRefs = artifactRefs.stream().map(this::fileRef).toList();
            structuredOutput.put("fileRefs", fileRefs.stream().map(AcademicToolFileRef::toMap).toList());
        } else if (!fileRefs.isEmpty() && !artifactRefs.isEmpty()) {
            fileRefs = mergeFileRefs(fileRefs, artifactRefs);
            structuredOutput.put("fileRefs", fileRefs.stream().map(AcademicToolFileRef::toMap).toList());
        }
        if (!structuredOutput.containsKey("toolName") && StringUtils.hasText(invocation.getToolName())) {
            structuredOutput.put("toolName", invocation.getToolName());
        }
        if (!structuredOutput.containsKey("artifactCount")) {
            structuredOutput.put("artifactCount", artifactRefs.size());
        }
        return AcademicToolOutputView.builder(invocation.getToolName())
                .requestId(invocation.getRequestId())
                .sessionId(invocation.getSessionId())
                .toolCallId(invocation.getToolCallId())
                .status(invocation.getStatus())
                .errorMessage(invocation.getErrorMessage())
                .createdAt(invocation.getFinishedAt() == null ? invocation.getStartedAt() : invocation.getFinishedAt())
                .structuredOutput(structuredOutput)
                .fileRefs(fileRefs)
                .artifactRefs(artifactRefs)
                .build();
    }

    private Map<String, Object> structuredOutput(AcademicToolInvocation invocation) {
        Map<String, Object> parsed = parseMap(invocation.getResultJson());
        if (!parsed.isEmpty()) {
            return new LinkedHashMap<>(parsed);
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("toolName", safe(invocation.getToolName()));
        if (StringUtils.hasText(invocation.getResultSummary())) {
            fallback.put("summary", invocation.getResultSummary());
        }
        if (StringUtils.hasText(invocation.getErrorMessage())) {
            fallback.put("errorMessage", invocation.getErrorMessage());
        }
        fallback.put("success", !AcademicAgentRun.STATUS_FAILED.equals(invocation.getStatus()));
        return fallback;
    }

    private Map<String, Object> parseMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<AcademicToolFileRef> fileRefs(Map<String, Object> structuredOutput) {
        if (structuredOutput == null || structuredOutput.isEmpty()) {
            return List.of();
        }
        List<AcademicToolFileRef> fileRefs = new ArrayList<>();
        collectFileRefs(structuredOutput.get("fileRefs"), fileRefs);
        collectFileRefs(structuredOutput.get("artifactRefs"), fileRefs);
        collectFileRefs(structuredOutput.get("fileInfo"), fileRefs);
        collectFileRefs(structuredOutput.get("fileList"), fileRefs);
        collectPrimaryFileRef(structuredOutput, fileRefs);
        collectNestedFileRefs(structuredOutput.get("result"), fileRefs);
        collectNestedFileRefs(structuredOutput.get("resultMap"), fileRefs);
        collectNestedFileRefs(structuredOutput.get("structuredOutput"), fileRefs);

        Map<String, AcademicToolFileRef> deduped = new LinkedHashMap<>();
        for (AcademicToolFileRef fileRef : fileRefs) {
            String key = firstText(fileRef.getArtifactId(), fileRef.getDownloadUrl(), fileRef.getPreviewUrl(), fileRef.getFileName());
            if (StringUtils.hasText(key)) {
                deduped.putIfAbsent(key, fileRef);
            }
        }
        return List.copyOf(deduped.values());
    }

    @SuppressWarnings("unchecked")
    private void collectFileRefs(Object value, List<AcademicToolFileRef> fileRefs) {
        if (!(value instanceof List<?> refs)) {
            return;
        }
        for (Object ref : refs) {
            if (ref instanceof Map<?, ?> map) {
                fileRefs.add(AcademicToolFileRef.fromMap((Map<String, Object>) map));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectNestedFileRefs(Object value, List<AcademicToolFileRef> fileRefs) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = (Map<String, Object>) map;
            collectFileRefs(nested.get("fileRefs"), fileRefs);
            collectFileRefs(nested.get("artifactRefs"), fileRefs);
            collectFileRefs(nested.get("fileInfo"), fileRefs);
            collectFileRefs(nested.get("fileList"), fileRefs);
            collectPrimaryFileRef(nested, fileRefs);
        }
    }

    private void collectPrimaryFileRef(Map<String, Object> values, List<AcademicToolFileRef> fileRefs) {
        if (values == null || values.isEmpty()) {
            return;
        }
        if (!hasPrimaryFilePayload(values)) {
            return;
        }
        AcademicToolFileRef fileRef = AcademicToolFileRef.fromMap(values);
        if (StringUtils.hasText(fileRef.getFileName())
                || StringUtils.hasText(fileRef.getDownloadUrl())
                || StringUtils.hasText(fileRef.getPreviewUrl())) {
            fileRefs.add(fileRef);
        }
    }

    private boolean hasPrimaryFilePayload(Map<String, Object> values) {
        return StringUtils.hasText(firstObjectText(
                values.get("primaryFileName"),
                values.get("fileName"),
                values.get("filename"),
                values.get("displayName"),
                values.get("name")))
                || StringUtils.hasText(firstObjectText(
                values.get("downloadUrl"),
                values.get("ossUrl"),
                values.get("domainUrl"),
                values.get("url"),
                values.get("previewUrl")));
    }

    private List<AcademicArtifact> artifactRefs(AcademicToolInvocation invocation,
                                                List<AcademicArtifact> artifacts,
                                                List<AcademicToolFileRef> fileRefs) {
        if (artifacts == null || artifacts.isEmpty()) {
            return List.of();
        }
        Map<String, AcademicArtifact> result = new LinkedHashMap<>();
        for (AcademicArtifact artifact : artifacts) {
            if (artifact == null) {
                continue;
            }
            if (same(artifact.getToolInvocationId(), invocation.getInvocationId())) {
                result.put(artifact.getArtifactId(), artifact);
            }
        }
        for (AcademicToolFileRef fileRef : fileRefs) {
            if (!StringUtils.hasText(fileRef.getArtifactId())) {
                continue;
            }
            for (AcademicArtifact artifact : artifacts) {
                if (artifact != null && same(artifact.getArtifactId(), fileRef.getArtifactId())) {
                    result.put(artifact.getArtifactId(), artifact);
                }
            }
        }
        return List.copyOf(result.values());
    }

    private AcademicToolFileRef fileRef(AcademicArtifact artifact) {
        return AcademicToolFileRef.builder()
                .artifactId(artifact.getArtifactId())
                .fileName(fileName(artifact))
                .downloadUrl(artifact.getDownloadUrl())
                .contentType(artifact.getArtifactType())
                .build();
    }

    private List<AcademicToolFileRef> mergeFileRefs(List<AcademicToolFileRef> fileRefs,
                                                    List<AcademicArtifact> artifacts) {
        List<AcademicToolFileRef> merged = new ArrayList<>();
        for (AcademicToolFileRef fileRef : fileRefs) {
            AcademicArtifact artifact = matchArtifact(fileRef, artifacts);
            if (artifact == null) {
                merged.add(fileRef);
                continue;
            }
            merged.add(AcademicToolFileRef.builder()
                    .artifactId(firstText(fileRef.getArtifactId(), artifact.getArtifactId()))
                    .fileName(firstText(fileRef.getFileName(), fileName(artifact)))
                    .downloadUrl(firstText(fileRef.getDownloadUrl(), artifact.getDownloadUrl()))
                    .previewUrl(fileRef.getPreviewUrl())
                    .contentType(firstText(fileRef.getContentType(), artifact.getArtifactType()))
                    .fileSize(fileRef.getFileSize())
                    .build());
        }
        return merged;
    }

    private AcademicArtifact matchArtifact(AcademicToolFileRef fileRef, List<AcademicArtifact> artifacts) {
        for (AcademicArtifact artifact : artifacts) {
            if (artifact == null) {
                continue;
            }
            if (same(artifact.getArtifactId(), fileRef.getArtifactId())) {
                return artifact;
            }
            if (StringUtils.hasText(fileRef.getFileName()) && same(fileName(artifact), fileRef.getFileName())) {
                return artifact;
            }
        }
        return null;
    }

    private String fileName(AcademicArtifact artifact) {
        String content = safe(artifact.getContent());
        int slash = Math.max(content.lastIndexOf('/'), content.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < content.length()) {
            return content.substring(slash + 1);
        }
        return StringUtils.hasText(content) ? content : safe(artifact.getTitle());
    }

    private boolean same(String left, String right) {
        return StringUtils.hasText(left) && left.equals(right);
    }

    private String firstObjectText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String firstText(String... values) {
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

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}















