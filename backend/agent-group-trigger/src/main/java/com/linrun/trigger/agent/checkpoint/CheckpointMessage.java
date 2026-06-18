package com.linrun.trigger.agent.checkpoint;

import lombok.Data;

import java.util.List;

/**
 * Spring AI {@code Message} 的序列化中间表示。
 *
 * <p>不直接序列化 Spring AI 的 Message 实现类（多态反序列化不稳定、跨版本易碎），
 * 而是拆成 role + content + toolCalls / toolResponses 的平铺结构，
 * 恢复时由 {@link AgentCheckpointSerializer} 重建对应的 SystemMessage / UserMessage /
 * AssistantMessage / ToolResponseMessage。
 */
@Data
public class CheckpointMessage {

    /** system / user / assistant / tool。 */
    private String role;

    /** 消息文本内容。 */
    private String content;

    /** 仅 assistant 消息携带：本轮 LLM 决定发起的工具调用。 */
    private List<ToolCall> toolCalls;

    /** 仅 tool 消息携带：上一轮工具调用的返回结果。 */
    private List<ToolResponse> toolResponses;

    /** 工具调用（assistant 侧）。 */
    public record ToolCall(String id, String name, String arguments) {
    }

    /** 工具返回（tool 侧）。 */
    public record ToolResponse(String id, String name, String response) {
    }
}
