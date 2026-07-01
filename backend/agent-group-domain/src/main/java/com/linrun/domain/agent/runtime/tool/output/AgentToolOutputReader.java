package com.linrun.domain.agent.runtime.tool.output;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.agent.ledger.model.AgentRun;
import com.linrun.domain.agent.ledger.model.AgentToolInvocation;
import com.linrun.domain.agent.model.AgentArtifact;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentToolOutputReader {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public AgentToolOutputReader() {
        this(new ObjectMapper());
    }

    public AgentToolOutputReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public AgentToolOutputView read(AgentToolInvocation invocation, List<AgentArtifact> artifacts) {
        if (invocation == null) {
            return AgentToolOutputView.builder("").build();
        }
        Map<String, Object> structuredOutput = structuredOutput(invocation);
        List<AgentToolFileRef> fileRefs = fileRefs(structuredOutput);
        List<AgentArtifact> artifactRefs = artifactRefs(invocation, artifacts, fileRefs);
        if (fileRefs.isEmpty() && !artifactRefs.isEmpty()) {
            fileRefs = artifactRefs.stream().map(this::fileRef).toList();
            structuredOutput.put("fileRefs", fileRefs.stream().map(AgentToolFileRef::toMap).toList());
        } else if (!fileRefs.isEmpty() && !artifactRefs.isEmpty()) {
            fileRefs = mergeFileRefs(fileRefs, artifactRefs);
            structuredOutput.put("fileRefs", fileRefs.stream().map(AgentToolFileRef::toMap).toList());
        }
        if (!structuredOutput.containsKey("toolName") && StringUtils.hasText(invocation.getToolName())) {
            structuredOutput.put("toolName", invocation.getToolName());
        }
        if (!structuredOutput.containsKey("artifactCount")) {
            structuredOutput.put("artifactCount", artifactRefs.size());
        }
        return AgentToolOutputView.builder(invocation.getToolName())
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

    private Map<String, Object> structuredOutput(AgentToolInvocation invocation) {
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
        fallback.put("success", !AgentRun.STATUS_FAILED.equals(invocation.getStatus()));
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
    public List<AgentToolFileRef> fileRefs(Map<String, Object> structuredOutput) {
        if (structuredOutput == null || structuredOutput.isEmpty()) {
            return List.of();
        }
        List<AgentToolFileRef> fileRefs = new ArrayList<>();
        collectFileRefs(structuredOutput.get("fileRefs"), fileRefs);
        collectFileRefs(structuredOutput.get("artifactRefs"), fileRefs);
        collectFileRefs(structuredOutput.get("fileInfo"), fileRefs);
        collectFileRefs(structuredOutput.get("fileList"), fileRefs);
        collectPrimaryFileRef(structuredOutput, fileRefs);
        collectNestedFileRefs(structuredOutput.get("result"), fileRefs);
        collectNestedFileRefs(structuredOutput.get("resultMap"), fileRefs);
        collectNestedFileRefs(structuredOutput.get("structuredOutput"), fileRefs);

        Map<String, AgentToolFileRef> deduped = new LinkedHashMap<>();
        for (AgentToolFileRef fileRef : fileRefs) {
            String key = firstText(fileRef.getArtifactId(), fileRef.getDownloadUrl(), fileRef.getPreviewUrl(), fileRef.getFileName());
            if (StringUtils.hasText(key)) {
                deduped.putIfAbsent(key, fileRef);
            }
        }
        return List.copyOf(deduped.values());
    }

    @SuppressWarnings("unchecked")
    private void collectFileRefs(Object value, List<AgentToolFileRef> fileRefs) {
        if (!(value instanceof List<?> refs)) {
            return;
        }
        for (Object ref : refs) {
            if (ref instanceof Map<?, ?> map) {
                fileRefs.add(AgentToolFileRef.fromMap((Map<String, Object>) map));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void collectNestedFileRefs(Object value, List<AgentToolFileRef> fileRefs) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = (Map<String, Object>) map;
            collectFileRefs(nested.get("fileRefs"), fileRefs);
            collectFileRefs(nested.get("artifactRefs"), fileRefs);
            collectFileRefs(nested.get("fileInfo"), fileRefs);
            collectFileRefs(nested.get("fileList"), fileRefs);
            collectPrimaryFileRef(nested, fileRefs);
        }
    }

    private void collectPrimaryFileRef(Map<String, Object> values, List<AgentToolFileRef> fileRefs) {
        if (values == null || values.isEmpty()) {
            return;
        }
        if (!hasPrimaryFilePayload(values)) {
            return;
        }
        AgentToolFileRef fileRef = AgentToolFileRef.fromMap(values);
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

    private List<AgentArtifact> artifactRefs(AgentToolInvocation invocation,
                                                List<AgentArtifact> artifacts,
                                                List<AgentToolFileRef> fileRefs) {
        if (artifacts == null || artifacts.isEmpty()) {
            return List.of();
        }
        Map<String, AgentArtifact> result = new LinkedHashMap<>();
        for (AgentArtifact artifact : artifacts) {
            if (artifact == null) {
                continue;
            }
            if (same(artifact.getToolInvocationId(), invocation.getInvocationId())) {
                result.put(artifact.getArtifactId(), artifact);
            }
        }
        for (AgentToolFileRef fileRef : fileRefs) {
            if (!StringUtils.hasText(fileRef.getArtifactId())) {
                continue;
            }
            for (AgentArtifact artifact : artifacts) {
                if (artifact != null && same(artifact.getArtifactId(), fileRef.getArtifactId())) {
                    result.put(artifact.getArtifactId(), artifact);
                }
            }
        }
        return List.copyOf(result.values());
    }

    private AgentToolFileRef fileRef(AgentArtifact artifact) {
        return AgentToolFileRef.builder()
                .artifactId(artifact.getArtifactId())
                .fileName(fileName(artifact))
                .downloadUrl(artifact.getDownloadUrl())
                .contentType(artifact.getArtifactType())
                .build();
    }

    private List<AgentToolFileRef> mergeFileRefs(List<AgentToolFileRef> fileRefs,
                                                    List<AgentArtifact> artifacts) {
        List<AgentToolFileRef> merged = new ArrayList<>();
        for (AgentToolFileRef fileRef : fileRefs) {
            AgentArtifact artifact = matchArtifact(fileRef, artifacts);
            if (artifact == null) {
                merged.add(fileRef);
                continue;
            }
            merged.add(AgentToolFileRef.builder()
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

    private AgentArtifact matchArtifact(AgentToolFileRef fileRef, List<AgentArtifact> artifacts) {
        for (AgentArtifact artifact : artifacts) {
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

    private String fileName(AgentArtifact artifact) {
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















