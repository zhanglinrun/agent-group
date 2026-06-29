package com.linrun.trigger.agent.checkpoint;

import com.linrun.trigger.agent.utils.SpringAiMessageFactory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 断点续跑核心：Message 序列化往返 + AgentCheckpoint JSON 往返。
 * 这是断点续跑能否真实成立的根基——上下文必须无损存取。
 */
class AgentCheckpointSerializerTest {

    private final AgentCheckpointSerializer serializer = new AgentCheckpointSerializer();

    @Test
    void messagesRoundTripPreservesRolesContentToolCallsAndResponses() {
        List<Message> messages = List.of(
                new SystemMessage("你是订单诊断助手"),
                new UserMessage("订单 O1 为什么额度没到账"),
                SpringAiMessageFactory.assistant("先查询一致性", List.of(
                        new AssistantMessage.ToolCall("call_1", "function", "trade_diagnosis",
                                "{\"orderId\":\"O1\"}"))),
                SpringAiMessageFactory.toolResponse(List.of(
                        new ToolResponseMessage.ToolResponse("call_1", "trade_diagnosis",
                                "{\"conclusion\":\"QUOTA_GRANT_REQUIRED\"}")))
        );

        List<CheckpointMessage> dtos = serializer.serializeMessages(messages);
        List<Message> restored = serializer.deserializeMessages(dtos);

        assertEquals(4, restored.size());
        assertEquals("你是订单诊断助手", restored.get(0).getText());
        assertEquals("订单 O1 为什么额度没到账", restored.get(1).getText());

        AssistantMessage assistant = (AssistantMessage) restored.get(2);
        assertEquals("先查询一致性", assistant.getText());
        assertEquals(1, assistant.getToolCalls().size());
        AssistantMessage.ToolCall call = assistant.getToolCalls().get(0);
        assertEquals("call_1", call.id());
        assertEquals("trade_diagnosis", call.name());
        assertEquals("{\"orderId\":\"O1\"}", call.arguments());

        ToolResponseMessage tool = (ToolResponseMessage) restored.get(3);
        assertEquals(1, tool.getResponses().size());
        assertTrue(tool.getResponses().get(0).responseData().contains("QUOTA_GRANT_REQUIRED"));
    }

    @Test
    void checkpointJsonRoundTripKeepsContinueTraceIdRoundAndSignatures() {
        AgentCheckpoint checkpoint = new AgentCheckpoint();
        checkpoint.setContinueTraceId("ckpt-abc");
        checkpoint.setAgentType("trade-diagnosis");
        checkpoint.setConversationId("S1");
        checkpoint.setQuestion("诊断 O1");
        checkpoint.setRound(3);
        checkpoint.setCurrentSessionId(99L);
        checkpoint.setExecutedSignatures(List.of("trade_diagnosis", "trade_order_list"));
        checkpoint.setMessages(serializer.serializeMessages(List.of(
                new SystemMessage("sys"), new UserMessage("q"))));

        AgentCheckpoint restored = serializer.fromJson(serializer.toJson(checkpoint));

        assertEquals("ckpt-abc", restored.getContinueTraceId());
        assertEquals("trade-diagnosis", restored.getAgentType());
        assertEquals(3, restored.getRound());
        assertEquals(99L, restored.getCurrentSessionId());
        assertEquals(List.of("trade_diagnosis", "trade_order_list"), restored.getExecutedSignatures());
        assertEquals(2, restored.getMessages().size());
        assertEquals("sys", restored.getMessages().get(0).getContent());
    }
}
