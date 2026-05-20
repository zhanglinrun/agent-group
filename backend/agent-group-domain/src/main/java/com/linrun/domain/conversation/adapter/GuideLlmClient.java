package com.linrun.domain.conversation.adapter;

import com.linrun.domain.conversation.model.GuideRagPrompt;
import com.linrun.domain.conversation.model.GuideLlmResult;
import com.linrun.domain.conversation.model.GuideTokenUsage;

public interface GuideLlmClient {

    String complete(GuideRagPrompt prompt);

    default GuideLlmResult completeWithMetrics(GuideRagPrompt prompt) {
        return GuideLlmResult.of(complete(prompt), GuideTokenUsage.empty(), 0L, false, "");
    }
}
