package com.linrun.domain.academic.runtime.agent;

import java.util.Map;

public record AcademicReActObservation(boolean success,
                                       String content,
                                       Map<String, Object> metadata) {

    public AcademicReActObservation {
        content = AcademicAgentValues.safe(content);
        metadata = AcademicAgentValues.copyMap(metadata);
    }

    public static AcademicReActObservation success(String content) {
        return success(content, Map.of());
    }

    public static AcademicReActObservation success(String content, Map<String, Object> metadata) {
        return new AcademicReActObservation(true, content, metadata);
    }

    public static AcademicReActObservation failed(String content) {
        return failed(content, Map.of());
    }

    public static AcademicReActObservation failed(String content, Map<String, Object> metadata) {
        return new AcademicReActObservation(false, content, metadata);
    }

}















