package com.linrun.trigger.agent.agent.deepresearch.runtime;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Spring AI Alibaba Graph 最小适配器。
 * 当前只负责把 deep 模式一轮闭环建模为 plan -> execute -> review -> final。
 */
public class GraphExecutionAdapter implements AgentRuntime<AgentRunContext, AgentRunContext> {

    private static final String PLAN = "plan";
    private static final String EXECUTE = "execute";
    private static final String REVIEW = "review";
    private static final String FINAL = "final";
    private static final String CONTEXT_KEY = "contextKey";
    private static final Map<String, AgentRunContext> CONTEXT_REGISTRY = new ConcurrentHashMap<>();

    private final CompiledGraph graph;
    private final Consumer<AgentRunContext> planAction;
    private final Consumer<AgentRunContext> executeAction;
    private final Consumer<AgentRunContext> reviewAction;
    private final Consumer<AgentRunContext> finalAction;

    public GraphExecutionAdapter(Consumer<AgentRunContext> planAction,
                                 Consumer<AgentRunContext> executeAction,
                                 Consumer<AgentRunContext> reviewAction,
                                 Consumer<AgentRunContext> finalAction) {
        this.planAction = planAction;
        this.executeAction = executeAction;
        this.reviewAction = reviewAction;
        this.finalAction = finalAction == null ? context -> { } : finalAction;
        this.graph = compileGraph();
    }

    @Override
    public AgentRunContext execute(AgentRunContext context) {
        String contextKey = UUID.randomUUID().toString();
        CONTEXT_REGISTRY.put(contextKey, context);
        try {
            graph.invoke(Map.of(CONTEXT_KEY, contextKey));
            return context;
        } finally {
            CONTEXT_REGISTRY.remove(contextKey);
        }
    }

    private CompiledGraph compileGraph() {
        try {
            return new StateGraph()
                    .addNode(PLAN, node(planAction))
                    .addNode(EXECUTE, node(executeAction))
                    .addNode(REVIEW, node(reviewAction))
                    .addNode(FINAL, node(finalAction))
                    .addEdge(StateGraph.START, PLAN)
                    .addEdge(PLAN, EXECUTE)
                    .addEdge(EXECUTE, REVIEW)
                    .addEdge(REVIEW, FINAL)
                    .addEdge(FINAL, StateGraph.END)
                    .compile();
        } catch (Exception e) {
            throw new IllegalStateException("Spring AI Alibaba Graph 初始化失败", e);
        }
    }

    private AsyncNodeAction node(Consumer<AgentRunContext> action) {
        return state -> CompletableFuture.supplyAsync(() -> {
            String contextKey = state.value(CONTEXT_KEY, "");
            AgentRunContext context = CONTEXT_REGISTRY.get(contextKey);
            if (context == null) {
                throw new IllegalStateException("AgentRunContext not found");
            }
            action.accept(context);
            return Map.of(CONTEXT_KEY, contextKey);
        });
    }
}
