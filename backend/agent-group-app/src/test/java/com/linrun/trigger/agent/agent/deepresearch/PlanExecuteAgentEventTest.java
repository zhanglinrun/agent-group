package com.linrun.trigger.agent.agent.deepresearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.trigger.agent.entity.record.CritiqueResult;
import com.linrun.trigger.agent.entity.record.PlanTask;
import com.linrun.trigger.agent.entity.record.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanExecuteAgentEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateInitialPlanUpdateEvent() throws Exception {
        String event = PlanExecuteAgent.createPlanUpdateEvent(1, List.of(
                new PlanTask("S1", "检索论文摘要", 1),
                new PlanTask("S2", "分析实验指标", 2)
        ));

        JsonNode root = objectMapper.readTree(event);

        assertEquals("plan_update", root.path("type").asText());
        assertEquals("深度研究执行计划", root.path("title").asText());
        assertEquals(List.of("S1", "S2"), root.path("structuredSteps").findValues("stepId").stream()
                .map(JsonNode::asText)
                .toList());
    }

    @Test
    void shouldCreateReplanEventAfterFirstRound() throws Exception {
        String event = PlanExecuteAgent.createPlanUpdateEvent(2, List.of(
                new PlanTask("R1", "改查实验结果表", 1)
        ));

        JsonNode root = objectMapper.readTree(event);

        assertEquals("replan", root.path("type").asText());
        assertEquals("第 2 轮补充执行计划", root.path("title").asText());
        assertEquals("R1", root.path("structuredSteps").get(0).path("stepId").asText());
    }

    @Test
    void shouldCreateReflectionEvent() throws Exception {
        String event = PlanExecuteAgent.createReflectionEvent(2,
                new CritiqueResult(false, "缺少多源对比"));

        JsonNode root = objectMapper.readTree(event);

        assertEquals("reflection", root.path("type").asText());
        assertEquals(2, root.path("round").asInt());
        assertEquals(false, root.path("passed").asBoolean());
        assertEquals("缺少多源对比", root.path("feedback").asText());
        assertEquals("replan", root.path("action").asText());
    }

    @Test
    void createsDeepResearchToolStartEvent() throws Exception {
        String json = PlanExecuteAgent.createDeepResearchToolStartEvent(
                "call-1",
                new PlanTask("T1", "查找资料", 2),
                "上一阶段结果");

        JsonNode node = objectMapper.readTree(json);
        assertEquals("tool_start", node.path("type").asText());
        assertEquals("deep_research_step", node.path("toolName").asText());
        assertEquals("T1", node.path("arguments").path("taskId").asText());
        assertEquals(2, node.path("arguments").path("order").asInt());
    }

    @Test
    void createsDeepResearchToolEndEventWithReferences() throws Exception {
        String json = PlanExecuteAgent.createDeepResearchToolEndEvent(
                "call-1",
                new PlanTask("T1", "查找资料", 1),
                true,
                "阶段结论",
                null,
                List.of(new SearchResult("https://example.com", "资料", "摘要")),
                System.currentTimeMillis());

        JsonNode node = objectMapper.readTree(json);
        assertEquals("tool_end", node.path("type").asText());
        assertEquals("success", node.path("status").asText());
        assertTrue(node.path("result").path("success").asBoolean());
        assertEquals(1, node.path("result").path("references").size());
    }
}
