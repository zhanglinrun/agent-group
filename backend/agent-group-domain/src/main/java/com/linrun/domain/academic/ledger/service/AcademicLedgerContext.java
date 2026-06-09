package com.linrun.domain.academic.ledger.service;

public final class AcademicLedgerContext {

    private static final ThreadLocal<Context> LOCAL = new ThreadLocal<>();

    private AcademicLedgerContext() {
    }

    public static void set(Context context) {
        if (context == null) {
            LOCAL.remove();
            return;
        }
        LOCAL.set(context);
    }

    public static Context current() {
        return LOCAL.get();
    }

    public static void clear() {
        LOCAL.remove();
    }

    public record Context(String runId,
                          String requestId,
                          String sessionId,
                          String userId,
                          String taskType) {
    }
}















