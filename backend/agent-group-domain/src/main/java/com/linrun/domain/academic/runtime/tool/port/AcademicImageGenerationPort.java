package com.linrun.domain.academic.runtime.tool.port;

import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;

import java.util.List;

public interface AcademicImageGenerationPort {

    String DEFAULT_MODEL = "gpt-image-2";
    String DEFAULT_QUALITY = "auto";
    String DEFAULT_ASPECT_RATIO = "1:1";

    AcademicImageGenerationResult generate(AcademicImageGenerationRequest request);

    record AcademicImageGenerationRequest(String prompt,
                                          String mode,
                                          String size,
                                          int batchCount,
                                          List<String> sourceImageUrls,
                                          List<String> maskImageUrls,
                                          String model,
                                          String quality,
                                          String aspectRatio,
                                          String baseUrl,
                                          String apiKey) {

        public AcademicImageGenerationRequest(String prompt,
                                              String mode,
                                              String size,
                                              int batchCount,
                                              List<String> sourceImageUrls,
                                              List<String> maskImageUrls,
                                              String model,
                                              String quality,
                                              String aspectRatio) {
            this(prompt, mode, size, batchCount, sourceImageUrls, maskImageUrls,
                    model, quality, aspectRatio, "", "");
        }

        public AcademicImageGenerationRequest(String prompt,
                                              String mode,
                                              String size,
                                              int batchCount,
                                              List<String> sourceImageUrls,
                                              List<String> maskImageUrls) {
            this(prompt, mode, size, batchCount, sourceImageUrls, maskImageUrls,
                    DEFAULT_MODEL, DEFAULT_QUALITY, DEFAULT_ASPECT_RATIO, "", "");
        }
    }

    record AcademicImageGenerationResult(boolean success,
                                         String provider,
                                         String summary,
                                         boolean usedFallback,
                                         List<AcademicToolFileRef> fileRefs,
                                         String errorMessage) {
    }
}















