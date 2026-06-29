package com.linrun.trigger.agent.agent.deepresearch;

import com.linrun.trigger.agent.agent.skills.runtime.SkillRuntimeDescriptor;
import com.linrun.trigger.agent.common.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
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
        assertTrue(summary.contains("status: degraded"));
        assertTrue(summary.contains("version: 1.0"));
        assertTrue(summary.contains("inputs: topic"));
        assertTrue(summary.contains("resources: skills/deep-report/SKILL.md"));
        assertFalse(summary.contains("tools: missing_tool"));
    }

    @Test
    void skillLoadedEventCarriesRuntimeAuditEvidence() {
        SkillRuntimeDescriptor skill = new SkillRuntimeDescriptor(
                "deep-report",
                "Deep report skill",
                List.of("deep"),
                List.of("deep"),
                List.of("topic"),
                "markdown report",
                List.of("workspace-read"),
                "1.0",
                true,
                List.of("read_skill", "missing_tool"),
                List.of("skills/deep-report/SKILL.md"));

        JsonNode event = JsonUtils.parse(PlanExecuteAgent.createSkillLoadedEvent(
                List.of(skill), Set.of("read_skill")));

        assertTrue(event.has("skills"));
        assertTrue(event.path("registeredTools").toString().contains("read_skill"));
        JsonNode firstSkill = event.path("skills").get(0);
        assertTrue(firstSkill.path("status").asText().equals("degraded"));
        assertTrue(firstSkill.path("usableTools").toString().contains("read_skill"));
        assertTrue(firstSkill.path("unavailableTools").toString().contains("missing_tool"));
        assertTrue(firstSkill.path("permissions").toString().contains("workspace-read"));
    }
}
