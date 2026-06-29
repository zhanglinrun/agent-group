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
                "tenant-a",
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
}
