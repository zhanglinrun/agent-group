package com.linrun.domain.agent.runtime.tool.port;

import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;

import java.util.List;

public interface AgentCodeInterpreterPort {

    String PERMISSION_PROFILE_ANALYSIS = "analysis";
    String PERMISSION_PROFILE_WORKSPACE = "workspace";

    AgentCodeExecutionResult execute(AgentCodeExecutionRequest request);

    static List<String> allowedPermissionProfiles() {
        return List.of(PERMISSION_PROFILE_ANALYSIS, PERMISSION_PROFILE_WORKSPACE);
    }

    static String normalizePermissionProfile(String value) {
        String text = value == null ? "" : value.trim().toLowerCase();
        if (text.isEmpty()) {
            return PERMISSION_PROFILE_ANALYSIS;
        }
        if (PERMISSION_PROFILE_ANALYSIS.equals(text) || PERMISSION_PROFILE_WORKSPACE.equals(text)) {
            return text;
        }
        throw new IllegalArgumentException("unsupported code interpreter permission profile: " + value);
    }

    record AgentCodeExecutionRequest(String task,
                                        String language,
                                        String code,
                                        List<String> fileNames,
                                        String permissionProfile) {
    }

    record AgentCodeExecutionResult(boolean success,
                                       Integer exitCode,
                                       String stdout,
                                       String stderr,
                                       String content,
                                       String code,
                                       String explain,
                                       List<AgentToolFileRef> fileRefs) {
    }
}















