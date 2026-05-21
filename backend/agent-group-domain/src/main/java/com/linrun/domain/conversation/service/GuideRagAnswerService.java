package com.linrun.domain.conversation.service;

import com.linrun.domain.conversation.adapter.GuideLlmClient;
import com.linrun.domain.conversation.model.GuideDecisionResult;
import com.linrun.domain.conversation.model.GuideLlmResult;
import com.linrun.domain.conversation.model.GuideRagAnswerResult;
import com.linrun.domain.conversation.model.GuideRagPrompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Service
public class GuideRagAnswerService {

    private final GuideRagPromptBuilder guideRagPromptBuilder;
    private final GuideLlmClient guideLlmClient;

    public GuideRagAnswerService(GuideRagPromptBuilder guideRagPromptBuilder, GuideLlmClient guideLlmClient) {
        this.guideRagPromptBuilder = guideRagPromptBuilder;
        this.guideLlmClient = guideLlmClient;
    }

    public List<String> answer(String question, GuideDecisionResult decisionResult) {
        return answerWithMetrics(question, decisionResult).getSegments();
    }

    public GuideRagAnswerResult answerWithMetrics(String question, GuideDecisionResult decisionResult) {
        GuideRagPrompt prompt = guideRagPromptBuilder.build(question, decisionResult);
        GuideLlmResult llmResult = guideLlmClient.completeWithMetrics(prompt);
        return toAnswerResult(prompt, llmResult, llmResult.getContent());
    }

    public GuideRagAnswerResult streamAnswerWithMetrics(String question,
                                                        GuideDecisionResult decisionResult,
                                                        Consumer<String> chunkSink,
                                                        BooleanSupplier stopped) {
        GuideRagPrompt prompt = guideRagPromptBuilder.build(question, decisionResult);
        StringBuilder answerBuffer = new StringBuilder();
        AtomicBoolean chunkEmitted = new AtomicBoolean(false);
        GuideLlmResult llmResult = guideLlmClient.streamWithMetrics(prompt, chunk -> {
            if (!StringUtils.hasText(chunk) || isStopped(stopped)) {
                return;
            }
            answerBuffer.append(chunk);
            chunkEmitted.set(true);
            if (chunkSink != null) {
                chunkSink.accept(chunk);
            }
        }, stopped);

        GuideRagAnswerResult answerResult = toAnswerResult(prompt, llmResult,
                answerBuffer.isEmpty() ? llmResult.getContent() : answerBuffer.toString());
        if (!chunkEmitted.get() && !isStopped(stopped)) {
            answerResult.getSegments().forEach(segment -> {
                if (!isStopped(stopped) && chunkSink != null) {
                    chunkSink.accept(segment + "\n");
                }
            });
        }
        return answerResult;
    }

    private GuideRagAnswerResult toAnswerResult(GuideRagPrompt prompt, GuideLlmResult llmResult, String answer) {
        boolean fallbackUsed = llmResult.isFallbackUsed();
        String effectiveAnswer = answer;
        if (!StringUtils.hasText(effectiveAnswer)) {
            effectiveAnswer = prompt.getFallbackAnswer();
            fallbackUsed = true;
        }
        List<String> segments = Arrays.stream(effectiveAnswer.split("\\R+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        return new GuideRagAnswerResult(segments, llmResult.getTokenUsage(), llmResult.getLatencyMillis(),
                fallbackUsed, llmResult.getModel());
    }

    private boolean isStopped(BooleanSupplier stopped) {
        return stopped != null && stopped.getAsBoolean();
    }
}
