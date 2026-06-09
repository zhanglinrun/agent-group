package com.linrun.trigger.agent.context;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BearDoctorTokenUsageRecorder {

    private static final ConcurrentMap<String, Accumulator> ACCUMULATORS = new ConcurrentHashMap<>();

    private BearDoctorTokenUsageRecorder() {
    }

    public static void start(String conversationId) {
        if (StringUtils.hasText(conversationId)) {
            ACCUMULATORS.put(conversationId, new Accumulator());
        }
    }

    public static void beginCall(String conversationId) {
        accumulator(conversationId).beginCall();
    }

    public static void record(String conversationId, ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return;
        }
        Usage usage = response.getMetadata().getUsage();
        long promptTokens = nonNegative(usage.getPromptTokens());
        long completionTokens = nonNegative(usage.getCompletionTokens());
        long totalTokens = nonNegative(usage.getTotalTokens());
        String model = response.getMetadata().getModel();
        accumulator(conversationId).record(promptTokens, completionTokens, totalTokens, model);
    }

    public static Snapshot snapshot(String conversationId) {
        Accumulator accumulator = ACCUMULATORS.get(conversationId);
        return accumulator == null ? Snapshot.empty() : accumulator.snapshot();
    }

    public static void clear(String conversationId) {
        if (StringUtils.hasText(conversationId)) {
            ACCUMULATORS.remove(conversationId);
        }
    }

    private static Accumulator accumulator(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return Accumulator.NOOP;
        }
        return ACCUMULATORS.computeIfAbsent(conversationId, ignored -> new Accumulator());
    }

    private static long nonNegative(Integer value) {
        return value == null ? 0L : Math.max(0L, value.longValue());
    }

    public record Snapshot(long promptTokens,
                           long completionTokens,
                           long totalTokens,
                           String model,
                           int callCount,
                           int responseCount) {

        public static Snapshot empty() {
            return new Snapshot(0L, 0L, 0L, "", 0, 0);
        }

        public boolean hasUsage() {
            return totalTokens > 0L || promptTokens > 0L || completionTokens > 0L;
        }
    }

    private static final class Accumulator {

        private static final Accumulator NOOP = new Accumulator(true);

        private final boolean noop;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private long lastPromptTokens;
        private long lastCompletionTokens;
        private long lastTotalTokens;
        private String model = "";
        private int callCount;
        private int responseCount;

        private Accumulator() {
            this(false);
        }

        private Accumulator(boolean noop) {
            this.noop = noop;
        }

        private synchronized void beginCall() {
            if (noop) {
                return;
            }
            lastPromptTokens = 0L;
            lastCompletionTokens = 0L;
            lastTotalTokens = 0L;
            callCount++;
        }

        private synchronized void record(long prompt, long completion, long total, String responseModel) {
            if (noop) {
                return;
            }
            long safePrompt = Math.max(0L, prompt);
            long safeCompletion = Math.max(0L, completion);
            long safeTotal = total > 0L ? total : safePrompt + safeCompletion;
            if (safePrompt <= 0L && safeCompletion <= 0L && safeTotal <= 0L) {
                return;
            }

            long promptDelta = delta(lastPromptTokens, safePrompt);
            long completionDelta = delta(lastCompletionTokens, safeCompletion);
            long totalDelta = delta(lastTotalTokens, safeTotal);
            long componentDelta = promptDelta + completionDelta;

            promptTokens += promptDelta;
            completionTokens += completionDelta;
            totalTokens += totalDelta > 0L ? totalDelta : componentDelta;
            lastPromptTokens = safePrompt;
            lastCompletionTokens = safeCompletion;
            lastTotalTokens = safeTotal;
            responseCount++;
            if (StringUtils.hasText(responseModel)) {
                model = responseModel;
            }
        }

        private long delta(long previous, long current) {
            if (current <= 0L) {
                return 0L;
            }
            return current >= previous ? current - previous : current;
        }

        private synchronized Snapshot snapshot() {
            return new Snapshot(promptTokens, completionTokens,
                    totalTokens > 0L ? totalTokens : promptTokens + completionTokens,
                    model, callCount, responseCount);
        }
    }
}















