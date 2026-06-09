package com.linrun.domain.academic.runtime.tool.port;

import java.util.List;
import java.util.Map;

public interface AcademicTableRagPort {

    AcademicTableRagResult recall(AcademicTableRagRequest request);

    record AcademicTableRagRequest(String requestId,
                                   String query,
                                   List<String> modelCodeList,
                                   String recallType,
                                   boolean useVector,
                                   boolean useElastic,
                                   int topK) {
    }

    record AcademicTableRagResult(boolean success,
                                  String requestId,
                                  List<AcademicTableSchemaMatch> matches,
                                  Map<String, Object> metadata,
                                  String errorMessage) {
    }

    record AcademicTableSchemaMatch(String modelCode,
                                    double score,
                                    List<Map<String, Object>> schemaList) {
    }
}















