package com.linrun.domain.agent.runtime.tool.port;

import java.util.List;
import java.util.Map;

public interface AgentTableRagPort {

    AgentTableRagResult recall(AgentTableRagRequest request);

    record AgentTableRagRequest(String requestId,
                                   String query,
                                   List<String> modelCodeList,
                                   String recallType,
                                   boolean useVector,
                                   boolean useElastic,
                                   int topK) {
    }

    record AgentTableRagResult(boolean success,
                                  String requestId,
                                  List<AgentTableSchemaMatch> matches,
                                  Map<String, Object> metadata,
                                  String errorMessage) {
    }

    record AgentTableSchemaMatch(String modelCode,
                                    double score,
                                    List<Map<String, Object>> schemaList) {
    }
}















