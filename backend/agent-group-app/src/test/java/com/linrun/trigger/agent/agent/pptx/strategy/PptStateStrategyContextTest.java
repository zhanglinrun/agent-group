package com.linrun.trigger.agent.agent.pptx.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.trigger.agent.entity.record.pptx.AiPptInst;
import com.linrun.trigger.agent.entity.record.pptx.PptInstStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PptStateStrategyContextTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private PptStateStrategyContext newContext() {
        return new PptStateStrategyContext(
                null, null, null, null, null, null,
                null, null, null, null, null, "");
    }

    @Test
    void createsStructuredPptStatusEvent() throws Exception {
        PptStateStrategyContext context = newContext();
        AiPptInst inst = AiPptInst.builder()
                .id(7L)
                .status(PptInstStatus.SCHEMA.getCode())
                .fileUrl("https://cdn.example.com/demo.pptx")
                .build();

        JsonNode node = objectMapper.readTree(
                context.createPptStatusResponse("SCHEMA", "进入PPT阶段：内容生成", inst));

        assertEquals("ppt_status", node.path("type").asText());
        assertEquals("SCHEMA", node.path("stage").asText());
        assertEquals("7", node.path("pptInstId").asText());
        assertEquals("SCHEMA", node.path("pptStatus").asText());
    }

    @Test
    void shouldContinueWhenDecisionMarkerIsContinue() {
        PptStateStrategyContext context = newContext();
        String response = "需求已明确，主题为AI入门。" +
                "<decision>{\"decision\":\"CONTINUE\",\"summary\":\"AI入门PPT\"}</decision>";

        assertTrue(context.shouldContinueToNextStep(response));
    }

    @Test
    void shouldPauseWhenDecisionMarkerIsPause() {
        PptStateStrategyContext context = newContext();
        String response = "还缺少受众信息。" +
                "<decision>{\"decision\":\"PAUSE\",\"summary\":\"需补充受众\"}</decision>";

        assertFalse(context.shouldContinueToNextStep(response));
    }

    @Test
    void shouldPauseWhenDecisionMarkerMissing() {
        PptStateStrategyContext context = newContext();
        assertFalse(context.shouldContinueToNextStep("没有结构化决策标记的纯文本"));
        assertFalse(context.shouldContinueToNextStep(""));
        assertFalse(context.shouldContinueToNextStep(null));
    }

    @Test
    void stripDecisionMarkerRemovesMarkerOnly() {
        PptStateStrategyContext context = newContext();
        String response = "需求摘要。" +
                "<decision>{\"decision\":\"CONTINUE\",\"summary\":\"x\"}</decision>";

        assertEquals("需求摘要。", context.stripDecisionMarker(response));
        assertNull(context.stripDecisionMarker(null));
    }
}

