package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.QuotaAccountResponse;
import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.api.dto.GuideStreamEvent;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.service.AcademicLedgerContext;
import com.linrun.domain.academic.runtime.agent.AcademicAgentPlan;
import com.linrun.domain.academic.runtime.agent.AcademicAgentRunPlanFactory;
import com.linrun.domain.academic.runtime.agent.AcademicPlanStep;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicBearDoctorAgentHandlerTest {

    @Test
    void shouldNormalizeManualSkillsTaskType() {
        AcademicBearDoctorAgentHandler handler = new AcademicBearDoctorAgentHandler(
                null, null, null, null, null, null, null, null, new ObjectMapper());

        assertEquals("manual-skills", ReflectionTestUtils.invokeMethod(handler, "normalizeTaskType", "manual"));
        assertEquals("manual-skills", ReflectionTestUtils.invokeMethod(handler, "normalizeTaskType", "manual-skills"));
        assertEquals("manual-skills", ReflectionTestUtils.invokeMethod(handler, "normalizeTaskType", "skills-manual"));
        assertEquals("manual-skills", ReflectionTestUtils.invokeMethod(handler, "toFrontendTaskType", "manual-skills"));
        assertEquals("image", ReflectionTestUtils.invokeMethod(handler, "normalizeTaskType", "workspace-image"));
        assertEquals("data", ReflectionTestUtils.invokeMethod(handler, "normalizeTaskType", "table-rag"));
        assertEquals("data", ReflectionTestUtils.invokeMethod(handler, "normalizeTaskType", "trade-flow"));
        assertEquals("data", ReflectionTestUtils.invokeMethod(handler, "normalizeTaskType", "workspace-trade"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStartWorkspaceTradeWithDataPlan() throws Exception {
        AcademicBearDoctorAgentHandler handler = new AcademicBearDoctorAgentHandler(
                null, null, null, null, null, null, null, null, new ObjectMapper());
        AcademicAgentRun run = new AcademicAgentRun();
        run.setRunId("RUN1000");
        run.setTaskType("data");
        run.setQuestion("analyze group trade order");
        run.setModelName("test-model");
        run.setStatus(AcademicAgentRun.STATUS_RUNNING);
        AcademicAgentPlan plan = new AcademicAgentRunPlanFactory().build("workspace-trade", false);

        List<GuideStreamEvent<?>> events = ReflectionTestUtils.invokeMethod(handler, "startEvents",
                runState(run, plan), "S1000", "REQ1000", new AtomicInteger(1));

        assertEquals(List.of("run_start", "plan_delta", "flow_delta"),
                events.stream().map(GuideStreamEvent::getEvent).toList());
        Map<String, Object> planData = (Map<String, Object>) events.get(1).getData();
        List<Map<String, Object>> structuredSteps = (List<Map<String, Object>>) planData.get("structuredSteps");
        List<Map<String, Object>> flowStages = (List<Map<String, Object>>) planData.get("flowStages");

        assertEquals("数据问答", planData.get("title"));
        assertEquals(5, structuredSteps.size());
        assertTrue(flowStages.stream().anyMatch(stage -> List.of("S4").equals(stage.get("stepIds"))));
    }

    @Test
    void shouldBuildExecutionMemoryPrompt() {
        AcademicBearDoctorAgentHandler handler = new AcademicBearDoctorAgentHandler(
                null, null, null, null, null, null, null, null, new ObjectMapper());

        AcademicSessionDetailResponse.MemorySnapshot memory = new AcademicSessionDetailResponse.MemorySnapshot();
        memory.setSummary("runs=1, toolObservations=1, reusableArtifacts=1");
        memory.setHistoryDialogue("Question: analyze group orders\nSummary: generated settlement report");

        AcademicSessionDetailResponse.RunMemory run = new AcademicSessionDetailResponse.RunMemory();
        run.setRequestId("REQ1");
        run.setTaskType("data");
        run.setQuestion("analyze group orders");
        memory.getRuns().add(run);

        AcademicSessionDetailResponse.ToolObservation observation = new AcademicSessionDetailResponse.ToolObservation();
        observation.setToolName("data_analysis");
        observation.setStatus("SUCCESS");
        observation.setResultSummary("settlement rate is 82%");
        memory.getToolObservations().add(observation);

        AcademicSessionDetailResponse.Artifact artifact = new AcademicSessionDetailResponse.Artifact();
        artifact.setArtifactId("ART1");
        artifact.setTitle("settlement report");
        artifact.setDownloadUrl("/api/artifacts/ART1/download");
        memory.getReusableArtifacts().add(artifact);

        String prompt = ReflectionTestUtils.invokeMethod(handler, "buildExecutionMemoryPrompt", memory);

        assertTrue(prompt.contains("Session execution memory"));
        assertTrue(prompt.contains("generated settlement report"));
        assertTrue(prompt.contains("settlement report"));
        assertTrue(prompt.contains("data_analysis"));
    }

    @Test
    void shouldBuildOutputStylePrompt() {
        AcademicBearDoctorAgentHandler handler = new AcademicBearDoctorAgentHandler(
                null, null, null, null, null, null, null, null, new ObjectMapper());

        String reportPrompt = ReflectionTestUtils.invokeMethod(handler, "outputStylePrompt", "report");
        String unknownPrompt = ReflectionTestUtils.invokeMethod(handler, "outputStylePrompt", "unknown");

        assertTrue(reportPrompt.contains("structured report"));
        assertEquals("", unknownPrompt);
        assertEquals("", ReflectionTestUtils.invokeMethod(handler,
                "effectiveOutputStyle", "data", ""));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldBuildQuotaUsageStructuredOutput() {
        AcademicBearDoctorAgentHandler handler = new AcademicBearDoctorAgentHandler(
                null, null, null, null, null, null, null, null, new ObjectMapper());
        QuotaAccountResponse quota = new QuotaAccountResponse();
        quota.setUserId("U1001");
        quota.setQuotaBalance(new BigDecimal("98"));
        quota.setUsedQuota(new BigDecimal("12"));
        quota.setFrozenQuota(BigDecimal.ZERO);

        Map<String, Object> output = ReflectionTestUtils.invokeMethod(handler,
                "quotaUsageStructuredOutput", quota, new BigDecimal("2"), "data", "test-model");
        Map<String, Object> metadata = (Map<String, Object>) output.get("metadata");

        assertEquals(AcademicToolOutputNames.QUOTA_USAGE, output.get("toolName"));
        assertEquals("额度对账快照", output.get("title"));
        assertEquals("data", metadata.get("taskType"));
        assertEquals("test-model", metadata.get("model"));
        assertEquals(new BigDecimal("2"), metadata.get("estimatedConsumedQuota"));
        assertEquals(new BigDecimal("98"), metadata.get("remainingQuota"));
        assertEquals(new BigDecimal("12"), metadata.get("usedQuota"));
        assertEquals(BigDecimal.ZERO, metadata.get("frozenQuota"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIncludeToolCallIdInToolResultEventData() {
        AcademicBearDoctorAgentHandler handler = new AcademicBearDoctorAgentHandler(
                null, null, null, null, null, null, null, null, new ObjectMapper());

        Map<String, Object> data = ReflectionTestUtils.invokeMethod(handler, "toolResult",
                "TOOL1001", "CALL1001", "report_tool", AcademicAgentRun.STATUS_SUCCESS,
                "{}", Map.of(), "", 12L);

        assertEquals("TOOL1001", data.get("invocationId"));
        assertEquals("CALL1001", data.get("toolCallId"));
        assertEquals("report_tool", data.get("toolName"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldConvertStructuredReplanEventToPlanAndFlowDeltas() throws Exception {
        AcademicBearDoctorAgentHandler handler = new AcademicBearDoctorAgentHandler(
                null, null, null, null, null, null, null, null, new ObjectMapper());
        AcademicAgentRun run = new AcademicAgentRun();
        run.setRunId("RUN1001");
        run.setTaskType("data");
        run.setQuestion("排查额度不到账");
        run.setModelName("test-model");
        run.setStatus(AcademicAgentRun.STATUS_RUNNING);
        AcademicAgentPlan plan = new AcademicAgentPlan("原计划", List.of(
                AcademicPlanStep.builder("S1", "查询订单").order(1).build(),
                AcademicPlanStep.builder("S2", "查询支付").order(2).dependencies(List.of("S1")).build()
        ));

        Object runState = runState(run, plan);
        String raw = """
                {
                  "type": "replan",
                  "reason": "支付表不可用，改查额度流水",
                  "title": "补救计划",
                  "structuredSteps": [
                    {"stepId": "R1", "instruction": "查询额度流水", "order": 1, "assignedAgent": "data"},
                    {"stepId": "R2", "instruction": "整理补偿建议", "order": 2, "dependencies": ["R1"]}
                  ]
                }
                """;

        List<GuideStreamEvent<?>> events = ReflectionTestUtils.invokeMethod(handler, "toEvents",
                raw, "S1001", "REQ1001", new AtomicInteger(1), runState);

        assertEquals(List.of("task_status", "flow_delta", "plan_delta", "flow_delta"),
                events.stream().map(GuideStreamEvent::getEvent).toList());
        Map<String, Object> oldFlowData = (Map<String, Object>) events.get(1).getData();
        assertEquals("REPLANNED", oldFlowData.get("status"));

        Map<String, Object> planData = (Map<String, Object>) events.get(2).getData();
        assertEquals("补救计划", planData.get("title"));
        List<Map<String, Object>> structuredSteps = (List<Map<String, Object>>) planData.get("structuredSteps");
        assertEquals(List.of("R1", "R2"), structuredSteps.stream().map(step -> step.get("stepId")).toList());

        Map<String, Object> flowData = (Map<String, Object>) events.get(3).getData();
        assertEquals("RUNNING", flowData.get("status"));
        assertEquals(List.of("R1"), flowData.get("stepIds"));
        assertTrue(String.valueOf(oldFlowData.get("message")).contains("支付表不可用"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldConvertPlanUpdateEventWithoutReplanStatus() throws Exception {
        AcademicBearDoctorAgentHandler handler = new AcademicBearDoctorAgentHandler(
                null, null, null, null, null, null, null, null, new ObjectMapper());
        AcademicAgentRun run = new AcademicAgentRun();
        run.setRunId("RUN1002");
        run.setTaskType("deep");
        run.setQuestion("研究拼团交易");
        run.setModelName("test-model");
        run.setStatus(AcademicAgentRun.STATUS_RUNNING);

        Object runState = runState(run, new AcademicAgentPlan("默认计划", List.of(
                AcademicPlanStep.builder("S1", "默认步骤").order(1).build()
        )));
        String raw = """
                {
                  "type": "plan_update",
                  "title": "深度研究执行计划",
                  "structuredSteps": [
                    {"stepId": "S1", "instruction": "检索拼团交易资料", "order": 1}
                  ]
                }
                """;

        List<GuideStreamEvent<?>> events = ReflectionTestUtils.invokeMethod(handler, "toEvents",
                raw, "S1002", "REQ1002", new AtomicInteger(1), runState);

        Map<String, Object> taskStatus = (Map<String, Object>) events.get(0).getData();
        Map<String, Object> flowData = (Map<String, Object>) events.get(2).getData();

        assertEquals("PLAN", taskStatus.get("stage"));
        assertEquals("RUNNING", flowData.get("status"));
        assertTrue(String.valueOf(flowData.get("message")).contains("计划已更新"));
    }

    private Object runState(AcademicAgentRun run, AcademicAgentPlan plan) throws Exception {
        Class<?> runStateClass = Class.forName("com.linrun.trigger.http.agent.AcademicBearDoctorAgentHandler$RunState");
        Constructor<?> constructor = runStateClass.getDeclaredConstructor(
                AcademicAgentRun.class,
                AcademicLedgerContext.Context.class,
                String.class,
                String.class,
                long.class,
                boolean.class,
                AcademicAgentPlan.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                run,
                new AcademicLedgerContext.Context(run.getRunId(), "REQ1001", "S1001", "U1001", run.getTaskType()),
                run.getQuestion(),
                run.getModelName(),
                System.currentTimeMillis(),
                false,
                plan);
    }
}
