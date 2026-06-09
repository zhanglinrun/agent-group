package com.linrun.domain.academic.runtime.tool.port;

import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;

import java.util.List;
import java.util.Map;

public interface AcademicDeepSearchPort {

    AcademicDeepSearchResult search(AcademicDeepSearchRequest request);

    record AcademicDeepSearchRequest(String query,
                                     int maxResults,
                                     boolean stream,
                                     List<String> sourceTypes,
                                     Map<String, Object> options) {
    }

    record AcademicDeepSearchResult(boolean success,
                                    String query,
                                    String answer,
                                    String answerSummary,
                                    List<String> subQueries,
                                    List<AcademicDeepSearchDocument> documents,
                                    List<AcademicToolFileRef> fileRefs,
                                    Map<String, Object> metadata,
                                    String errorMessage) {
    }

    record AcademicDeepSearchDocument(String title,
                                      String url,
                                      String content,
                                      String source) {
    }
}















