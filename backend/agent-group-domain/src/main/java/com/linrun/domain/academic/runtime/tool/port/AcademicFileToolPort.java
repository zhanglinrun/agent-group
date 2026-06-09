package com.linrun.domain.academic.runtime.tool.port;

import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;

import java.util.List;
import java.util.Map;

public interface AcademicFileToolPort {

    AcademicFileToolResult upload(AcademicFileUploadRequest request);

    AcademicFileToolResult get(AcademicFileGetRequest request);

    record AcademicFileUploadRequest(String requestId,
                                     String fileName,
                                     String description,
                                     String content,
                                     String contentType,
                                     boolean internalFile) {
    }

    record AcademicFileGetRequest(String requestId,
                                  String fileName,
                                  int maxContentChars) {
    }

    record AcademicFileToolResult(boolean success,
                                  String command,
                                  String fileName,
                                  String content,
                                  String summary,
                                  List<AcademicToolFileRef> fileRefs,
                                  Map<String, Object> metadata,
                                  String errorMessage) {
    }
}















