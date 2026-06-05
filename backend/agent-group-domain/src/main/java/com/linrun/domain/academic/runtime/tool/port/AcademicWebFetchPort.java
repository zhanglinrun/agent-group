package com.linrun.domain.academic.runtime.tool.port;

import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;

import java.util.List;
import java.util.Map;

public interface AcademicWebFetchPort {

    AcademicWebFetchResult fetch(AcademicWebFetchRequest request);

    record AcademicWebFetchRequest(String requestId,
                                   String url,
                                   int timeoutSeconds,
                                   int maxContentChars) {
    }

    record AcademicWebFetchResult(boolean success,
                                  String title,
                                  String finalUrl,
                                  String content,
                                  String summary,
                                  List<AcademicToolFileRef> fileRefs,
                                  Map<String, Object> metadata,
                                  String errorMessage) {
    }
}
