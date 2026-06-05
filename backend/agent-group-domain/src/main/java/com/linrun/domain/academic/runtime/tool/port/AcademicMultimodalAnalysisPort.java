package com.linrun.domain.academic.runtime.tool.port;

import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;

import java.util.List;
import java.util.Map;

public interface AcademicMultimodalAnalysisPort {

    AcademicMultimodalAnalysisResult analyze(AcademicMultimodalAnalysisRequest request);

    record AcademicMultimodalAnalysisRequest(String task,
                                             String text,
                                             List<String> imageUrls,
                                             List<String> fileUrls) {
    }

    record AcademicMultimodalAnalysisResult(boolean success,
                                            String summary,
                                            String content,
                                            Map<String, Object> metadata,
                                            List<AcademicToolFileRef> fileRefs,
                                            String errorMessage) {
    }
}
