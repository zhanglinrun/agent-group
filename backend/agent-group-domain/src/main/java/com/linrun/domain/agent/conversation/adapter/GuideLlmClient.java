package com.linrun.domain.agent.conversation.adapter;

import com.linrun.domain.agent.conversation.model.GuideRagPrompt;
import com.linrun.domain.agent.conversation.model.GuideLlmResult;
import com.linrun.domain.agent.conversation.model.GuideTokenUsage;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface GuideLlmClient {

    String complete(GuideRagPrompt prompt);

    default GuideLlmResult completeWithMetrics(GuideRagPrompt prompt) {
        return GuideLlmResult.of(complete(prompt), GuideTokenUsage.empty(), 0L, false, "");
    }

    default GuideLlmResult streamWithMetrics(GuideRagPrompt prompt,
                                             Consumer<String> chunkSink,
                                             BooleanSupplier stopped) {
        GuideLlmResult result = completeWithMetrics(prompt);
        emitFallbackChunks(result.getContent(), chunkSink, stopped);
        return result;
    }

    private void emitFallbackChunks(String content, Consumer<String> chunkSink, BooleanSupplier stopped) {
        if (content == null || chunkSink == null || (stopped != null && stopped.getAsBoolean())) {
            return;
        }
        Arrays.stream(content.split("\\R+"))
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .takeWhile(segment -> stopped == null || !stopped.getAsBoolean())
                .forEach(segment -> chunkSink.accept(segment + "\n"));
    }
}
