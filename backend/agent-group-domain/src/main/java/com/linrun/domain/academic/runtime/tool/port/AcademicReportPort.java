package com.linrun.domain.academic.runtime.tool.port;

import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;

import java.util.List;
import java.util.Map;

public interface AcademicReportPort {

    AcademicReportResult generate(AcademicReportRequest request);

    record AcademicReportRequest(String requestId,
                                 String task,
                                 String title,
                                 String summary,
                                 List<Map<String, Object>> sections,
                                 List<String> evidence,
                                 List<String> fileNames,
                                 String fileName,
                                 String fileType,
                                 String templateType,
                                 boolean stream) {
    }

    record AcademicReportResult(boolean success,
                                String content,
                                String summary,
                                List<AcademicToolFileRef> fileRefs,
                                Map<String, Object> metadata,
                                String errorMessage) {
    }
}















