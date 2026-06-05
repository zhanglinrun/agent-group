package com.linrun.domain.academic.runtime.tool.port;

import java.util.List;
import java.util.Map;

public interface AcademicNl2SqlPort {

    AcademicNl2SqlResult convert(AcademicNl2SqlRequest request);

    record AcademicNl2SqlRequest(String requestId,
                                 String query,
                                 List<String> modelCodeList,
                                 List<Map<String, Object>> schemaInfo,
                                 String currentDateInfo,
                                 String dbType,
                                 boolean stream,
                                 boolean useVector,
                                 boolean useElastic) {
    }

    record AcademicNl2SqlResult(boolean success,
                                String requestId,
                                String rootQuery,
                                String think,
                                String status,
                                List<AcademicSqlCandidate> candidates,
                                Map<String, Object> metadata,
                                String errorMessage) {
    }

    record AcademicSqlCandidate(String query,
                                String sql) {
    }
}
