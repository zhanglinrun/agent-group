package com.linrun.trigger.agent.agent.deepresearch;

import com.linrun.trigger.agent.agent.skills.runtime.SkillRuntimeDescriptor;
import com.linrun.trigger.agent.agent.deepresearch.runtime.AgentRunContext;
import com.linrun.trigger.agent.agent.deepresearch.runtime.AgentMemorySnapshot;
import com.linrun.trigger.agent.common.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.linrun.trigger.agent.entity.OverAllState;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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

    @Test
    void contextLoadedEventCarriesRoleScopedEvidence() {
        AgentRunContext context = AgentRunContext.builder()
                .mode("deep")
                .taskType("research")
                .traceId("T1001")
                .spanId("SP1001")
                .state(new OverAllState("S1001", "研究动态技能"))
                .sink(Sinks.many().unicast().onBackpressureBuffer())
                .finished(new AtomicBoolean(false))
                .thinkingBuffer(new StringBuilder())
                .build();

        JsonNode event = JsonUtils.parse(PlanExecuteAgent.createContextLoadedEvent(
                context, Set.of("read_skill")));

        assertTrue(event.path("roles").has("planner"));
        assertTrue(event.path("roles").has("worker"));
        assertTrue(event.path("roles").has("reviewer"));
        assertTrue(event.path("roles").path("planner").toString().contains("goal"));
        assertTrue(event.path("roles").path("worker").toString().contains("registeredTools"));
        assertTrue(event.path("capabilities").toString().contains("read_skill"));
    }

    @Test
    void memoryLoadedEventCarriesLayeredMemoryEvidenceOnly() {
        AgentMemorySnapshot memory = new AgentMemorySnapshot(
                "U1001",
                "S1001",
                List.of("short"),
                List.of("task"),
                List.of("preference: 喜欢报告式回答"),
                true);

        JsonNode event = JsonUtils.parse(PlanExecuteAgent.createMemoryLoadedEvent(memory));

        assertTrue(event.path("memory").path("shortTermCount").asInt() == 1);
        assertTrue(event.path("memory").path("taskMemoryCount").asInt() == 1);
        assertTrue(event.path("memory").path("longTermCount").asInt() == 1);
        assertTrue(event.path("memory").path("longTermEnabled").asBoolean());
    }

    @Test
    void capabilityLoadedEventCarriesRuntimeEvidence() {
        AgentRunContext context = AgentRunContext.builder()
                .mode("deep")
                .taskType("research")
                .state(new OverAllState("S1001", "研究动态技能"))
                .sink(Sinks.many().unicast().onBackpressureBuffer())
                .finished(new AtomicBoolean(false))
                .thinkingBuffer(new StringBuilder())
                .memorySnapshot(new AgentMemorySnapshot(
                        "U1001",
                        "S1001",
                        List.of("short"),
                        List.of("task"),
                        List.of(),
                        false))
                .availableSkills(List.of(new SkillRuntimeDescriptor(
                        "deep-report",
                        "Deep report skill",
                        List.of("deep"),
                        List.of("research"),
                        List.of("topic"),
                        "markdown report",
                        List.of("workspace-read"),
                        "1.0",
                        true,
                        List.of("read_skill"),
                        List.of("skills/deep-report/SKILL.md"))))
                .build();

        JsonNode event = JsonUtils.parse(PlanExecuteAgent.createCapabilityLoadedEvent(
                context, Set.of("read_skill"), "spring-ai-alibaba-graph"));

        JsonNode capability = event.path("capability");
        assertTrue(capability.path("runtime").asText().equals("spring-ai-alibaba-graph"));
        assertTrue(capability.path("skillCount").asInt() == 1);
        assertTrue(capability.path("capabilityCount").asInt() == 2);
        assertFalse(capability.path("memory").path("longTermEnabled").asBoolean());
        assertTrue(capability.path("runtimeEvidence").toString().contains("skills_are_runtime_checked"));
    }

    @Test
    void capabilityCalledEventCarriesRealRuntimeCallEvidence() {
        SkillRuntimeDescriptor skill = new SkillRuntimeDescriptor(
                "deep-report",
                "Deep report skill",
                List.of("deep"),
                List.of("research"),
                List.of("topic"),
                "markdown report",
                List.of("workspace-read"),
                "1.0",
                true,
                List.of("read_skill"),
                List.of("skills/deep-report/SKILL.md"));

        JsonNode event = JsonUtils.parse(PlanExecuteAgent.createCapabilityCalledEvent(
                "CALL1001",
                "deep_research_step",
                "execute_step",
                new com.linrun.trigger.agent.entity.record.PlanTask("T1", "整理证据", 1),
                Set.of("read_skill"),
                List.of(skill)));

        assertTrue(event.path("type").asText().equals("capability_called"));
        assertTrue(event.path("capabilityName").asText().equals("deep_research_step"));
        assertTrue(event.path("arguments").path("skills").toString().contains("deep-report"));
        assertTrue(event.path("arguments").path("registeredTools").toString().contains("read_skill"));
    }
}
