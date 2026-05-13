package com.linrun.domain.guide.adapter;

import com.linrun.domain.guide.model.GuideRagPrompt;

public interface GuideLlmClient {

    String complete(GuideRagPrompt prompt);
}
