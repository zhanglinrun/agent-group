package com.linrun.domain.support.trace;

import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 链路追踪上下文。
 * 支持 TraceId 和 SpanId 传播，用于全链路追踪。
 */
public class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> SPAN_ID = new ThreadLocal<>();
    private static final ThreadLocal<Deque<String>> PARENT_SPAN_STACK = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, String>> TAGS = new ThreadLocal<>();

    /**
     * 开始新的追踪。
     */
    public static void startTrace(String traceId) {
        TRACE_ID.set(traceId != null ? traceId : generateTraceId());
        SPAN_ID.set(generateSpanId());
        PARENT_SPAN_STACK.set(new ArrayDeque<>());
        TAGS.set(new HashMap<>());
    }

    /**
     * 开始新的 Span。
     */
    public static String startSpan(String operation) {
        String currentSpan = SPAN_ID.get();
        String newSpan = generateSpanId();

        Deque<String> stack = parentSpanStack();
        if (currentSpan != null) {
            stack.push(currentSpan);
        }
        SPAN_ID.set(newSpan);

        addTag("operation", operation);

        return newSpan;
    }

    /**
     * 结束当前 Span
     */
    public static void endSpan() {
        Deque<String> stack = PARENT_SPAN_STACK.get();
        if (stack != null && !stack.isEmpty()) {
            SPAN_ID.set(stack.pop());
        }
    }

    /**
     * 添加标签
     */
    public static void addTag(String key, String value) {
        Map<String, String> tags = TAGS.get();
        if (tags == null) {
            tags = new HashMap<>();
            TAGS.set(tags);
        }
        tags.put(key, value);
    }

    /**
     * 获取当前 TraceId
     */
    public static String currentTraceId() {
        return TRACE_ID.get();
    }

    /**
     * 获取当前 SpanId
     */
    public static String currentSpanId() {
        return SPAN_ID.get();
    }

    /**
     * 获取父级 SpanId。
     */
    public static String parentSpanId() {
        Deque<String> stack = PARENT_SPAN_STACK.get();
        return stack == null || stack.isEmpty() ? null : stack.peek();
    }

    /**
     * 获取所有标签。
     */
    public static Map<String, String> getTags() {
        Map<String, String> tags = TAGS.get();
        return tags != null ? new HashMap<>(tags) : new HashMap<>();
    }

    /**
     * 清理上下文。
     */
    public static void clear() {
        TRACE_ID.remove();
        SPAN_ID.remove();
        PARENT_SPAN_STACK.remove();
        TAGS.remove();
    }

    /**
     * 生成 TraceId
     */
    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成 SpanId
     */
    private static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static Deque<String> parentSpanStack() {
        Deque<String> stack = PARENT_SPAN_STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            PARENT_SPAN_STACK.set(stack);
        }
        return stack;
    }

    /**
     * 追踪信息快照
     */
    public static class TraceSnapshot {
        private final String traceId;
        private final String spanId;
        private final String parentSpanId;
        private final List<String> parentSpanStack;
        private final Map<String, String> tags;

        public TraceSnapshot(String traceId, String spanId, String parentSpanId, 
                           Map<String, String> tags) {
            this(traceId, spanId, parentSpanId,
                    parentSpanId == null ? List.of() : List.of(parentSpanId),
                    tags);
        }

        private TraceSnapshot(String traceId,
                              String spanId,
                              String parentSpanId,
                              List<String> parentSpanStack,
                              Map<String, String> tags) {
            this.traceId = traceId;
            this.spanId = spanId;
            this.parentSpanId = parentSpanId;
            this.parentSpanStack = parentSpanStack == null ? List.of() : new ArrayList<>(parentSpanStack);
            this.tags = tags != null ? new HashMap<>(tags) : new HashMap<>();
        }

        public String getTraceId() {
            return traceId;
        }

        public String getSpanId() {
            return spanId;
        }

        public String getParentSpanId() {
            return parentSpanId;
        }

        public Map<String, String> getTags() {
            return tags;
        }

        private List<String> getParentSpanStack() {
            return parentSpanStack;
        }

        @Override
        public String toString() {
            return String.format("TraceSnapshot{traceId='%s', spanId='%s', parentSpanId='%s', tags=%s}",
                    traceId, spanId, parentSpanId, tags);
        }
    }

    /**
     * 获取当前追踪快照
     */
    public static TraceSnapshot snapshot() {
        Deque<String> stack = PARENT_SPAN_STACK.get();
        return new TraceSnapshot(
                currentTraceId(),
                currentSpanId(),
                parentSpanId(),
                stack == null ? List.of() : new ArrayList<>(stack),
                getTags()
        );
    }

    /**
     * 从快照恢复上下文
     */
    public static void restore(TraceSnapshot snapshot) {
        if (snapshot == null) {
            clear();
            return;
        }
        TRACE_ID.set(snapshot.getTraceId());
        SPAN_ID.set(snapshot.getSpanId());
        PARENT_SPAN_STACK.set(new ArrayDeque<>(snapshot.getParentSpanStack()));
        TAGS.set(new HashMap<>(snapshot.getTags()));
    }
}














