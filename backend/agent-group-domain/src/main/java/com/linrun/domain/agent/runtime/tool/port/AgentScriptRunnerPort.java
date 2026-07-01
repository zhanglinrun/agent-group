package com.linrun.domain.agent.runtime.tool.port;

import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;

import java.util.List;
import java.util.Map;

public interface AgentScriptRunnerPort {

    AgentScriptRunResult run(AgentScriptRunRequest request);

    record AgentScriptRunRequest(String requestId,
                                    String skillName,
                                    String skillBasePath,
                                    String scriptName,
                                    String scriptPath,
                                    String runtime,
                                    Map<String, Object> arguments,
                                    List<String> argv,
                                    int timeoutSeconds) {
    }

    record AgentScriptRunResult(boolean success,
                                   Integer exitCode,
                                   String stdout,
                                   String stderr,
                                   String summary,
                                   List<AgentToolFileRef> fileRefs,
                                   Map<String, Object> metadata,
                                   String errorMessage) {
    }
}















