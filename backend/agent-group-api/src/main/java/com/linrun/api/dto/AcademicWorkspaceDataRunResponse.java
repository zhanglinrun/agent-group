package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AcademicWorkspaceDataRunResponse implements Serializable {

    private String requestId;
    private String sessionId;
    private String runId;
    private String summary;
    private List<String> missingTools = new ArrayList<>();
    private List<ToolResult> toolResults = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Data
    public static class ToolResult implements Serializable {
        private String invocationId;
        private String toolName;
        private String title;
        private String summary;
        private String content;
        private Map<String, Object> structuredOutput = new LinkedHashMap<>();
        private List<Map<String, Object>> fileRefs = new ArrayList<>();
    }
}
