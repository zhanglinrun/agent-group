package com.linrun.domain.guide.service;

import com.linrun.domain.guide.adapter.GuideLlmClient;
import com.linrun.domain.guide.model.GuideDecisionResult;
import com.linrun.domain.guide.model.GuideRagPrompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Service
public class GuideRagAnswerService {

    private final GuideRagPromptBuilder guideRagPromptBuilder;
    private final GuideLlmClient guideLlmClient;

    public GuideRagAnswerService(GuideRagPromptBuilder guideRagPromptBuilder, GuideLlmClient guideLlmClient) {
        this.guideRagPromptBuilder = guideRagPromptBuilder;
        this.guideLlmClient = guideLlmClient;
    }

    public List<String> answer(String question, GuideDecisionResult decisionResult) {
        GuideRagPrompt prompt = guideRagPromptBuilder.build(question, decisionResult);
        String answer = guideLlmClient.complete(prompt);
        if (!StringUtils.hasText(answer)) {
            answer = prompt.getFallbackAnswer();
        }
        return Arrays.stream(answer.split("\\R+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
