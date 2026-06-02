package cn.hollis.llm.mentor.agent.entity.event;

/**
 * 工具执行记录。
 *
 * @param toolName   工具名称
 * @param toolCallId 工具调用 ID
 * @param arguments  工具调用参数 JSON
 * @param result     工具返回结果
 * @author bigchui
 */
public record ToolRecord(
        String toolName,
        String toolCallId,
        String arguments,
        String result
) {
}
