package com.linrun.domain.academic.runtime.tool.port;

import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;

import java.util.List;
import java.util.Map;

public interface AcademicDataAnalysisPort {

    AcademicDataAnalysisResult analyze(AcademicDataAnalysisRequest request);

    record AcademicDataAnalysisRequest(String requestId,
                                       String task,
                                       List<Map<String, Object>> rows,
                                       List<String> columns,
                                       List<String> modelCodeList,
                                       String businessKnowledge,
                                       int maxSteps,
                                       boolean stream) {
    }

    record AcademicDataAnalysisResult(boolean success,
                                      String content,
                                      String summary,
                                      List<AcademicToolFileRef> fileRefs,
                                      Map<String, Object> metadata,
                                      String errorMessage) {
    }
}















