package com.linrun.domain.academic.runtime.tool.port;

import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;

import java.util.List;

public interface AcademicImageGenerationPort {

    AcademicImageGenerationResult generate(AcademicImageGenerationRequest request);

    record AcademicImageGenerationRequest(String prompt,
                                          String mode,
                                          String size,
                                          int batchCount,
                                          List<String> sourceImageUrls,
                                          List<String> maskImageUrls) {
    }

    record AcademicImageGenerationResult(boolean success,
                                         String provider,
                                         String summary,
                                         boolean usedFallback,
                                         List<AcademicToolFileRef> fileRefs,
                                         String errorMessage) {
    }
}
