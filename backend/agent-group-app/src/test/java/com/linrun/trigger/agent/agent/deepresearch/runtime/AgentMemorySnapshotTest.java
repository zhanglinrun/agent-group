package com.linrun.trigger.agent.agent.deepresearch.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMemorySnapshotTest {

    @Test
    void longTermMemoryRespectsPrivacySwitch() {
        AgentMemorySnapshot memory = new AgentMemorySnapshot(
                "U1001",
                "S1001",
                List.of("short"),
                List.of("task"),
                List.of("private preference"),
                false);

        Map<String, Object> evidence = memory.evidence();

        assertEquals(1, evidence.get("shortTermCount"));
        assertEquals(1, evidence.get("taskMemoryCount"));
        assertEquals(0, evidence.get("longTermCount"));
        assertTrue(memory.longTerm().isEmpty());
    }

    @Test
    void longTermMemoryCanBeInjectedWhenEnabled() {
        AgentMemorySnapshot memory = new AgentMemorySnapshot(
                "U1001",
                "S1001",
                List.of("short"),
                List.of("task"),
                List.of("preference: 喜欢报告式回答"),
                true);

        Map<String, Object> evidence = memory.evidence();

        assertEquals(1, evidence.get("longTermCount"));
        assertEquals("preference: 喜欢报告式回答", memory.longTerm().get(0));
    }
}
