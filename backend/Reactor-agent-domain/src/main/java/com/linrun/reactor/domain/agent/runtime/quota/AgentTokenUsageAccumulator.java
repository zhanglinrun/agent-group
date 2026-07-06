package com.linrun.reactor.domain.agent.runtime.quota;

import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * 单次 Agent run 的 LLM token 用量累计器。
 */
public class AgentTokenUsageAccumulator {

    private final LongAdder promptTokens = new LongAdder();
    private final LongAdder completionTokens = new LongAdder();
    private final LongAdder totalTokens = new LongAdder();
    private final LongAdder durationMillis = new LongAdder();
    private final AtomicReference<String> modelName = new AtomicReference<>();
    private final AtomicBoolean estimated = new AtomicBoolean(false);
    private final AtomicBoolean settled = new AtomicBoolean(false);

    public void record(Integer promptTokenCount,
                       Integer completionTokenCount,
                       Integer totalTokenCount,
                       String usedModelName,
                       long invocationDurationMillis) {
        long safePromptTokens = positiveValue(promptTokenCount);
        long safeCompletionTokens = positiveValue(completionTokenCount);
        long safeTotalTokens = positiveValue(totalTokenCount);
        long resolvedTotalTokens = safeTotalTokens > 0L
                ? safeTotalTokens
                : safePromptTokens + safeCompletionTokens;

        if (safePromptTokens > 0L) {
            promptTokens.add(safePromptTokens);
        }
        if (safeCompletionTokens > 0L) {
            completionTokens.add(safeCompletionTokens);
        }
        if (resolvedTotalTokens > 0L) {
            totalTokens.add(resolvedTotalTokens);
        }
        if (invocationDurationMillis > 0L) {
            durationMillis.add(invocationDurationMillis);
        }
        if (StringUtils.isNotBlank(usedModelName)) {
            modelName.compareAndSet(null, usedModelName.trim());
        }
    }

    public void recordEstimated(String promptText,
                                String completionText,
                                String usedModelName,
                                long invocationDurationMillis) {
        long estimatedPromptTokens = estimateTokens(promptText);
        long estimatedCompletionTokens = estimateTokens(completionText);
        long estimatedTotalTokens = estimatedPromptTokens + estimatedCompletionTokens;
        if (estimatedTotalTokens <= 0L) {
            return;
        }
        record(toInteger(estimatedPromptTokens),
                toInteger(estimatedCompletionTokens),
                toInteger(estimatedTotalTokens),
                StringUtils.defaultIfBlank(usedModelName, "reactor-agent-estimated"),
                invocationDurationMillis);
        estimated.set(true);
    }

    public UsageSnapshot snapshot() {
        return new UsageSnapshot(
                promptTokens.sum(),
                completionTokens.sum(),
                totalTokens.sum(),
                modelName.get(),
                durationMillis.sum(),
                estimated.get()
        );
    }

    public boolean markSettled() {
        return settled.compareAndSet(false, true);
    }

    private long positiveValue(Integer value) {
        return value == null ? 0L : Math.max(0L, value.longValue());
    }

    private Integer toInteger(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, value);
    }

    private long estimateTokens(String text) {
        if (StringUtils.isBlank(text)) {
            return 0L;
        }
        long cjkTokens = 0L;
        long otherChars = 0L;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            Character.UnicodeScript script = Character.UnicodeScript.of(ch);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                cjkTokens++;
            } else {
                otherChars++;
            }
        }
        return cjkTokens + (long) Math.ceil(otherChars / 4.0d);
    }

    public record UsageSnapshot(long promptTokens,
                                long completionTokens,
                                long totalTokens,
                                String modelName,
                                long durationMillis,
                                boolean estimated) {

        public boolean hasTokenUsage() {
            return promptTokens > 0L || completionTokens > 0L || totalTokens > 0L;
        }
    }
}
