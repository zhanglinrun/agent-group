package com.linrun.trigger.agent.checkpoint;

import com.linrun.trigger.agent.utils.SpringAiMessageFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 断点快照序列化器：在 Spring AI {@link Message} 与平铺的 {@link CheckpointMessage} 之间转换，
 * 并把整个 {@link AgentCheckpoint} 编解码为 JSON。
 *
 * <p>不依赖 Spring AI Message 的多态反序列化，保证跨版本稳定；工具调用 / 工具返回都显式拆字段重建。
 */
public class AgentCheckpointSerializer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String toJson(AgentCheckpoint checkpoint) {
        try {
            return objectMapper.writeValueAsString(checkpoint);
        } catch (Exception e) {
            throw new IllegalStateException("checkpoint 序列化失败：" + e.getMessage(), e);
        }
    }

    public AgentCheckpoint fromJson(String json) {
        try {
            return objectMapper.readValue(json, AgentCheckpoint.class);
        } catch (Exception e) {
            throw new IllegalStateException("checkpoint 反序列化失败：" + e.getMessage(), e);
        }
    }

    public List<CheckpointMessage> serializeMessages(List<Message> messages) {
        List<CheckpointMessage> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (Message message : messages) {
            result.add(serialize(message));
        }
        return result;
    }

    private CheckpointMessage serialize(Message message) {
        CheckpointMessage dto = new CheckpointMessage();
        if (message instanceof SystemMessage sm) {
            dto.setRole("system");
            dto.setContent(sm.getText());
        } else if (message instanceof UserMessage um) {
            dto.setRole("user");
            dto.setContent(um.getText());
        } else if (message instanceof AssistantMessage am) {
            dto.setRole("assistant");
            dto.setContent(am.getText());
            List<AssistantMessage.ToolCall> calls = am.getToolCalls();
            if (calls != null && !calls.isEmpty()) {
                dto.setToolCalls(calls.stream()
                        .map(call -> new CheckpointMessage.ToolCall(call.id(), call.name(), call.arguments()))
                        .toList());
            }
        } else if (message instanceof ToolResponseMessage tm) {
            dto.setRole("tool");
            List<ToolResponseMessage.ToolResponse> responses = tm.getResponses();
            if (responses != null && !responses.isEmpty()) {
                dto.setToolResponses(responses.stream()
                        .map(response -> new CheckpointMessage.ToolResponse(
                                response.id(), response.name(), response.responseData()))
                        .toList());
            }
        } else {
            // 兜底：未识别的消息类型退化为 user 文本，避免丢失上下文。
            dto.setRole("user");
            dto.setContent(message.getText());
        }
        return dto;
    }

    public List<Message> deserializeMessages(List<CheckpointMessage> messages) {
        List<Message> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (CheckpointMessage dto : messages) {
            Message message = deserialize(dto);
            if (message != null) {
                result.add(message);
            }
        }
        return result;
    }

    private Message deserialize(CheckpointMessage dto) {
        String role = dto.getRole();
        if ("system".equals(role)) {
            return new SystemMessage(StringUtils.hasText(dto.getContent()) ? dto.getContent() : "");
        }
        if ("user".equals(role)) {
            return new UserMessage(StringUtils.hasText(dto.getContent()) ? dto.getContent() : "");
        }
        if ("assistant".equals(role)) {
            List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
            if (dto.getToolCalls() != null) {
                for (CheckpointMessage.ToolCall call : dto.getToolCalls()) {
                    toolCalls.add(new AssistantMessage.ToolCall(call.id(), "function", call.name(), call.arguments()));
                }
            }
            return SpringAiMessageFactory.assistant(
                    StringUtils.hasText(dto.getContent()) ? dto.getContent() : "",
                    toolCalls);
        }
        if ("tool".equals(role)) {
            List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
            if (dto.getToolResponses() != null) {
                for (CheckpointMessage.ToolResponse response : dto.getToolResponses()) {
                    responses.add(new ToolResponseMessage.ToolResponse(response.id(), response.name(), response.response()));
                }
            }
            return SpringAiMessageFactory.toolResponse(responses);
        }
        return null;
    }
}
