package com.linrun.domain.guide.adapter;

import com.linrun.domain.guide.model.GuideRagPrompt;
import com.linrun.domain.guide.model.GuideLlmResult;
import com.linrun.domain.guide.model.GuideTokenUsage;

public interface GuideLlmClient {

    String complete(GuideRagPrompt prompt);

    default GuideLlmResult completeWithMetrics(GuideRagPrompt prompt) {
        return GuideLlmResult.of(complete(prompt), GuideTokenUsage.empty(), 0L, false, "");
    }
}
