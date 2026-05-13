package com.linrun.infrastructure.llm;

import com.linrun.domain.guide.adapter.GuideLlmClient;
import com.linrun.domain.guide.model.GuideRagPrompt;
import org.springframework.stereotype.Component;

@Component
public class LocalGuideLlmClient implements GuideLlmClient {

    @Override
    public String complete(GuideRagPrompt prompt) {
        return prompt.getFallbackAnswer();
    }
}
