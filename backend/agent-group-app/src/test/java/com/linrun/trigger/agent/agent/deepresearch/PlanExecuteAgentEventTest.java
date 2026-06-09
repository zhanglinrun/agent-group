package com.linrun.trigger.agent.agent.deepresearch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.trigger.agent.entity.record.PlanTask;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanExecuteAgentEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateInitialPlanUpdateEvent() throws Exception {
        String event = PlanExecuteAgent.createPlanUpdateEvent(1, List.of(
                new PlanTask("S1", "检索论文摘�?, 1),
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
                new PlanTask("R1", "改查实验结果行, 1)
        ));

        JsonNode root = objectMapper.readTree(event);

        assertEquals("replan", root.path("type").asText());
        assertEquals("�?2 轮补充执行计�?, root.path("title").asText());
        assertEquals("R1", root.path("structuredSteps").get(0).path("stepId").asText());
    }
}















