package com.linrun.domain.support.trace;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TraceContextTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void shouldRestoreNestedSpansInStackOrder() {
        TraceContext.startTrace("TRACE1001");
        String rootSpan = TraceContext.currentSpanId();
        String firstSpan = TraceContext.startSpan("first");
        String secondSpan = TraceContext.startSpan("second");

        TraceContext.TraceSnapshot snapshot = TraceContext.snapshot();
        TraceContext.clear();
        TraceContext.restore(snapshot);

        assertEquals("TRACE1001", TraceContext.currentTraceId());
        assertEquals(secondSpan, TraceContext.currentSpanId());
        assertEquals(firstSpan, TraceContext.parentSpanId());

        TraceContext.endSpan();
        assertEquals(firstSpan, TraceContext.currentSpanId());
        assertEquals(rootSpan, TraceContext.parentSpanId());

        TraceContext.endSpan();
        assertEquals(rootSpan, TraceContext.currentSpanId());
        assertNull(TraceContext.parentSpanId());
    }

    @Test
    void shouldClearContextWhenRestoreNullSnapshot() {
        TraceContext.startTrace("TRACE1002");
        TraceContext.startSpan("tool");

        TraceContext.restore(null);

        assertNull(TraceContext.currentTraceId());
        assertNull(TraceContext.currentSpanId());
        assertNull(TraceContext.parentSpanId());
    }

    @Test
    void shouldGenerateDashlessSixteenCharSpanIds() {
        TraceContext.startTrace("TRACE1003");

        assertEquals(16, TraceContext.currentSpanId().length());
        assertEquals(16, TraceContext.startSpan("model").length());
    }
}
