package com.linrun.domain.academic.runtime.tool.port;

import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;

import java.util.List;
import java.util.Map;

public interface AcademicScriptRunnerPort {

    AcademicScriptRunResult run(AcademicScriptRunRequest request);

    record AcademicScriptRunRequest(String requestId,
                                    String skillName,
                                    String skillBasePath,
                                    String scriptName,
                                    String scriptPath,
                                    String runtime,
                                    Map<String, Object> arguments,
                                    List<String> argv,
                                    int timeoutSeconds) {
    }

    record AcademicScriptRunResult(boolean success,
                                   Integer exitCode,
                                   String stdout,
                                   String stderr,
                                   String summary,
                                   List<AcademicToolFileRef> fileRefs,
                                   Map<String, Object> metadata,
                                   String errorMessage) {
    }
}
