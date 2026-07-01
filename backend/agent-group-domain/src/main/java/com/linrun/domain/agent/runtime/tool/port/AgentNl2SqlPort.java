package com.linrun.domain.agent.runtime.tool.port;

import java.util.List;
import java.util.Map;

public interface AgentNl2SqlPort {

    AgentNl2SqlResult convert(AgentNl2SqlRequest request);

    record AgentNl2SqlRequest(String requestId,
                                 String query,
                                 List<String> modelCodeList,
                                 List<Map<String, Object>> schemaInfo,
                                 String currentDateInfo,
                                 String dbType,
                                 boolean stream,
                                 boolean useVector,
                                 boolean useElastic) {
    }

    record AgentNl2SqlResult(boolean success,
                                String requestId,
                                String rootQuery,
                                String think,
                                String status,
                                List<AgentSqlCandidate> candidates,
                                Map<String, Object> metadata,
                                String errorMessage) {
    }

    record AgentSqlCandidate(String query,
                                String sql) {
    }
}















