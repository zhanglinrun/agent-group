package com.linrun.domain.agent.runtime.tool.port;

import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;

import java.util.List;
import java.util.Map;

public interface AgentDataAnalysisPort {

    AgentDataAnalysisResult analyze(AgentDataAnalysisRequest request);

    record AgentDataAnalysisRequest(String requestId,
                                       String task,
                                       List<Map<String, Object>> rows,
                                       List<String> columns,
                                       List<String> modelCodeList,
                                       String businessKnowledge,
                                       int maxSteps,
                                       boolean stream) {
    }

    record AgentDataAnalysisResult(boolean success,
                                      String content,
                                      String summary,
                                      List<AgentToolFileRef> fileRefs,
                                      Map<String, Object> metadata,
                                      String errorMessage) {
    }
}















