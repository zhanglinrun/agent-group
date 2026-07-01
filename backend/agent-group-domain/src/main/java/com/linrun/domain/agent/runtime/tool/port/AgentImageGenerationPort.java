package com.linrun.domain.agent.runtime.tool.port;

import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;

import java.util.List;

public interface AgentImageGenerationPort {

    String DEFAULT_MODEL = "gpt-image-2";
    String DEFAULT_QUALITY = "auto";
    String DEFAULT_ASPECT_RATIO = "1:1";

    AgentImageGenerationResult generate(AgentImageGenerationRequest request);

    record AgentImageGenerationRequest(String prompt,
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

        public AgentImageGenerationRequest(String prompt,
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

        public AgentImageGenerationRequest(String prompt,
                                              String mode,
                                              String size,
                                              int batchCount,
                                              List<String> sourceImageUrls,
                                              List<String> maskImageUrls) {
            this(prompt, mode, size, batchCount, sourceImageUrls, maskImageUrls,
                    DEFAULT_MODEL, DEFAULT_QUALITY, DEFAULT_ASPECT_RATIO, "", "");
        }
    }

    record AgentImageGenerationResult(boolean success,
                                         String provider,
                                         String summary,
                                         boolean usedFallback,
                                         List<AgentToolFileRef> fileRefs,
                                         String errorMessage) {
    }
}















