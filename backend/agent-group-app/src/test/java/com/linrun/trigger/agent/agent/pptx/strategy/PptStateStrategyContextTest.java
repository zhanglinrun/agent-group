package com.linrun.trigger.agent.agent.pptx.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.trigger.agent.entity.record.pptx.AiPptInst;
import com.linrun.trigger.agent.entity.record.pptx.PptInstStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PptStateStrategyContextTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsStructuredPptStatusEvent() throws Exception {
        PptStateStrategyContext context = new PptStateStrategyContext(
                null, null, null, null, null, null,
                null, null, null, null, null, "");
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
}
