package com.linrun.trigger.agent.agent.deepresearch.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class LocalSubAgentToolFactory {

    private LocalSubAgentToolFactory() {
    }

    public static ToolCallback[] create() {
        return new ToolCallback[]{
                subAgentTool("file_reader_agent",
                        "本地文件理解子 Agent。用于把当前步骤中涉及的文件、片段或证据整理成可执行摘要。"),
                subAgentTool("report_reviewer_agent",
                        "本地报告评审子 Agent。用于检查研究输出是否覆盖目标、证据、风险和下一步建议。")
        };
    }

    private static ToolCallback subAgentTool(String name, String description) {
        Function<Request, Map<String, Object>> function = request -> response(name, request);
        return FunctionToolCallback.builder(name, function)
                .description(description + "\n返回 JSON，包含 subAgent、status、summary、handoff。")
                .inputType(Request.class)
                .build();
    }

    private static Map<String, Object> response(String subAgent, Request request) {
        String task = clean(request == null ? "" : request.task());
        String context = clean(request == null ? "" : request.context());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subAgent", subAgent);
        data.put("status", StringUtils.hasText(task) ? "accepted" : "needs_task");
        data.put("summary", summarize(task, context));
        data.put("handoff", List.of(
                "parent_agent_keeps_control",
                "tool_call_is_auditable",
                "no_quota_or_trade_state_mutation"));
        return data;
    }

    private static String summarize(String task, String context) {
        if (!StringUtils.hasText(task)) {
            return "缺少子 Agent 任务描述。";
        }
        String suffix = StringUtils.hasText(context) ? "；上下文摘要：" + limit(context, 160) : "";
        return limit(task, 180) + suffix;
    }

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }

    private static String limit(String text, int maxChars) {
        if (!StringUtils.hasText(text) || text.length() <= maxChars) {
            return clean(text);
        }
        return text.substring(0, maxChars) + "...";
    }

    public record Request(
            @JsonProperty(required = true)
            @JsonPropertyDescription("交给本地子 Agent 的明确任务") String task,
            @JsonPropertyDescription("父 Agent 裁剪后的上下文，不要传完整对话") String context
    ) {
    }
}
