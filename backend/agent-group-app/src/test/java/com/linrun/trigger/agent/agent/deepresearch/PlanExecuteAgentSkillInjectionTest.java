package com.linrun.trigger.agent.agent.deepresearch;

import com.linrun.trigger.agent.agent.skills.runtime.SkillRuntimeDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanExecuteAgentSkillInjectionTest {

    @Test
    void workerSkillSummarySeparatesUsableAndMissingBoundTools() {
        SkillRuntimeDescriptor skill = new SkillRuntimeDescriptor(
                "deep-report",
                "Deep report skill",
                List.of("deep"),
                List.of("deep"),
                List.of("topic"),
                "markdown report",
                List.of(),
                "1.0",
                true,
                List.of("read_skill", "missing_tool"),
                List.of("skills/deep-report/SKILL.md"));

        String summary = skill.toWorkerSummary(Set.of("read_skill"));

        assertTrue(summary.contains("tools: read_skill"));
        assertTrue(summary.contains("unavailableTools: missing_tool"));
        assertTrue(summary.contains("inputs: topic"));
        assertTrue(summary.contains("resources: skills/deep-report/SKILL.md"));
        assertFalse(summary.contains("tools: missing_tool"));
    }
}
