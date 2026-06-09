package com.linrun.trigger.agent.entity.record;

import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;

import static java.util.Collections.synchronizedList;

/**
 * Agent 轮次执行状�?
 * 保存每轮执行时的中间状�?
 */
@Data
public class RoundState {
    /** 当前运行模式 */
    public RoundMode mode = RoundMode.UNKNOWN;

    /** 文本缓冲�?*/
    public StringBuilder textBuffer = new StringBuilder();

    /** 待确认文本缓冲区，避免模型把工具调用前导语提前透出 */
    public StringBuilder pendingTextBuffer = new StringBuilder();

    /** 原始文本缓冲区，用于识别模型误输出的伪工具调用文�?*/
    public StringBuilder rawTextBuffer = new StringBuilder();

    /** 模型误把工具调用写进正文后，停止继续透传该段文本 */
    public boolean pseudoToolCallTextDetected = false;

    /** 工具调用列表 */
    public List<AssistantMessage.ToolCall> toolCalls = synchronizedList(new java.util.ArrayList<>());

    /** ThinkTagParser �?inThink 状态，�?chunk 追踪 <think/> 标签 */
    public boolean inThink = false;

}















