package com.linrun.domain.academic.runtime.artifact;

import org.springframework.util.StringUtils;

public class AcademicToolArtifactSource {

    private final String runId;
    private final String toolInvocationId;
    private final String toolName;
    private final String sourceType;
    private final String sourceName;

    public AcademicToolArtifactSource(String runId,
                                      String toolInvocationId,
                                      String toolName,
                                      String sourceType,
                                      String sourceName) {
        this.runId = safe(runId);
        this.toolInvocationId = safe(toolInvocationId);
        this.toolName = safe(toolName);
        this.sourceType = StringUtils.hasText(sourceType) ? sourceType.trim() : "TOOL";
        this.sourceName = StringUtils.hasText(sourceName) ? sourceName.trim() : this.toolName;
    }

    public static AcademicToolArtifactSource of(String runId,
                                                String toolInvocationId,
                                                String toolName) {
        return new AcademicToolArtifactSource(runId, toolInvocationId, toolName, "TOOL", toolName);
    }

    public String getRunId() {
        return runId;
    }

    public String getToolInvocationId() {
        return toolInvocationId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
