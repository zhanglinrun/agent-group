package com.linrun.trigger.agent.utils;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.List;
import java.util.Map;

/**
 * Spring AI message construction helpers.
 */
public final class SpringAiMessageFactory {

    private SpringAiMessageFactory() {
    }

    public static AssistantMessage assistant(String content,
                                             List<AssistantMessage.ToolCall> toolCalls) {
        return AssistantMessage.builder()
                .content(content == null ? "" : content)
                .properties(Map.of())
                .toolCalls(toolCalls == null ? List.of() : toolCalls)
                .media(List.of())
                .build();
    }

    public static ToolResponseMessage toolResponse(List<ToolResponseMessage.ToolResponse> responses) {
        return ToolResponseMessage.builder()
                .responses(responses == null ? List.of() : responses)
                .metadata(Map.of())
                .build();
    }
}
