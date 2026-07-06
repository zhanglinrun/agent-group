package com.linrun.reactor.test.domain;

import com.linrun.reactor.domain.agent.adapter.port.AgentQuotaPort;
import com.linrun.reactor.domain.agent.ledger.AgentExecutionRecorder;
import com.linrun.reactor.domain.agent.ledger.ExecutionLedgerRunSupport;
import com.linrun.reactor.domain.agent.ledger.model.ArtifactRecordCommand;
import com.linrun.reactor.domain.agent.ledger.model.DialogueRunFinishRecord;
import com.linrun.reactor.domain.agent.ledger.model.DialogueRunStartRecord;
import com.linrun.reactor.domain.agent.ledger.model.LlmInvocationFinishRecord;
import com.linrun.reactor.domain.agent.ledger.model.LlmInvocationStartRecord;
import com.linrun.reactor.domain.agent.ledger.model.ToolInvocationBatchStartRecord;
import com.linrun.reactor.domain.agent.ledger.model.ToolInvocationFinishRecord;
import com.linrun.reactor.domain.agent.runtime.ReactorRuntimeDependencies;
import com.linrun.reactor.domain.agent.runtime.agent.AgentContext;
import com.linrun.reactor.domain.agent.runtime.quota.AgentTokenUsageAccumulator;
import com.linrun.reactor.domain.agent.reactor.model.req.AgentRequest;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * Reactor Agent 账本收尾与本项目额度结算接缝测试。
 */
public class ExecutionLedgerQuotaSettlementTest {

    @Test
    public void shouldPrecheckAndSettleQuotaOnceWithAccumulatedTokenUsage() {
        RecordingAgentQuotaPort quotaPort = new RecordingAgentQuotaPort();
        RecordingExecutionRecorder recorder = new RecordingExecutionRecorder();
        AgentContext agentContext = AgentContext.builder()
                .requestId("REQ-1001")
                .sessionId("SESSION-1001")
                .executionRecorder(recorder)
                .runtimeDependencies(ReactorRuntimeDependencies.builder()
                        .agentQuotaPort(quotaPort)
                        .build())
                .build();
        AgentRequest request = new AgentRequest();
        request.setRequestId("REQ-1001");
        request.setSessionId("SESSION-1001");
        request.setVisitorId("USER-1001");
        request.setQuery("生成一份市场分析报告");

        ExecutionLedgerRunSupport.initializeRun(recorder, agentContext, request, "react");
        agentContext.recordLlmUsage(12, 8, 20, "qwen-plus", 100L);
        agentContext.recordLlmUsage(5, 7, null, "qwen-max", 150L);

        ExecutionLedgerRunSupport.finishRun(agentContext, 1, "已完成", null, null);
        ExecutionLedgerRunSupport.finishRun(agentContext, 1, "重复收尾", null, null);

        Assert.assertEquals("USER-1001", quotaPort.precheckUserId);
        Assert.assertEquals("react", quotaPort.precheckTaskType);
        Assert.assertEquals(1, quotaPort.settleCount);
        Assert.assertEquals("USER-1001", quotaPort.settleUserId);
        Assert.assertEquals("SESSION-1001", quotaPort.settleSessionId);
        Assert.assertEquals("REQ-1001", quotaPort.settleTaskConsumeBizId);
        Assert.assertEquals("react", quotaPort.settleTaskType);
        Assert.assertNotNull(quotaPort.settledUsageSnapshot);
        Assert.assertEquals(17L, quotaPort.settledUsageSnapshot.promptTokens());
        Assert.assertEquals(15L, quotaPort.settledUsageSnapshot.completionTokens());
        Assert.assertEquals(32L, quotaPort.settledUsageSnapshot.totalTokens());
        Assert.assertEquals("qwen-plus", quotaPort.settledUsageSnapshot.modelName());
        Assert.assertEquals(250L, quotaPort.settledUsageSnapshot.durationMillis());
        Assert.assertFalse(quotaPort.settledUsageSnapshot.estimated());
        Assert.assertEquals(Long.valueOf(10001L), agentContext.getAgentRunState().getRunId());
        Assert.assertEquals("REQ-1001", recorder.finishRecord.getRequestId());
    }

    @Test
    public void shouldSkipQuotaSettlementWhenNoTokenUsageWasRecorded() {
        RecordingAgentQuotaPort quotaPort = new RecordingAgentQuotaPort();
        RecordingExecutionRecorder recorder = new RecordingExecutionRecorder();
        AgentContext agentContext = AgentContext.builder()
                .requestId("REQ-1002")
                .sessionId("SESSION-1002")
                .executionRecorder(recorder)
                .runtimeDependencies(ReactorRuntimeDependencies.builder()
                        .agentQuotaPort(quotaPort)
                        .build())
                .build();
        AgentRequest request = new AgentRequest();
        request.setRequestId("REQ-1002");
        request.setSessionId("SESSION-1002");
        request.setVisitorId("USER-1002");

        ExecutionLedgerRunSupport.initializeRun(recorder, agentContext, request, "plan_solve");
        ExecutionLedgerRunSupport.finishRun(agentContext, 1, "无用量", null, null);

        Assert.assertEquals("USER-1002", quotaPort.precheckUserId);
        Assert.assertEquals("plan_solve", quotaPort.precheckTaskType);
        Assert.assertEquals(0, quotaPort.settleCount);
    }

    @Test
    public void shouldSettleEstimatedUsageWhenProviderUsageIsMissing() {
        AgentTokenUsageAccumulator accumulator = new AgentTokenUsageAccumulator();
        accumulator.recordEstimated("分析这段中文内容", "done", "agent-workspace-estimated", 35L);

        AgentTokenUsageAccumulator.UsageSnapshot snapshot = accumulator.snapshot();

        Assert.assertTrue(snapshot.hasTokenUsage());
        Assert.assertEquals(8L, snapshot.promptTokens());
        Assert.assertEquals(1L, snapshot.completionTokens());
        Assert.assertEquals(9L, snapshot.totalTokens());
        Assert.assertEquals("agent-workspace-estimated", snapshot.modelName());
        Assert.assertEquals(35L, snapshot.durationMillis());
        Assert.assertTrue(snapshot.estimated());
    }

    @Test
    public void shouldSettleQuotaWhenRunFailedAfterUsageWasRecorded() {
        RecordingAgentQuotaPort quotaPort = new RecordingAgentQuotaPort();
        RecordingExecutionRecorder recorder = new RecordingExecutionRecorder();
        AgentContext agentContext = AgentContext.builder()
                .requestId("REQ-1003")
                .sessionId("SESSION-1003")
                .executionRecorder(recorder)
                .runtimeDependencies(ReactorRuntimeDependencies.builder()
                        .agentQuotaPort(quotaPort)
                        .build())
                .build();
        AgentRequest request = new AgentRequest();
        request.setRequestId("REQ-1003");
        request.setSessionId("SESSION-1003");
        request.setVisitorId("USER-1003");

        ExecutionLedgerRunSupport.initializeRun(recorder, agentContext, request, "react");
        agentContext.recordEstimatedLlmUsage("失败前已经调用模型", null, "reactor-agent-estimated", 80L);
        ExecutionLedgerRunSupport.finishRun(agentContext, 0, null, "REACT_EXECUTE_ERROR", "tool failed");

        Assert.assertEquals(1, quotaPort.settleCount);
        Assert.assertEquals("USER-1003", quotaPort.settleUserId);
        Assert.assertEquals("REQ-1003", quotaPort.settleTaskConsumeBizId);
        Assert.assertNotNull(quotaPort.settledUsageSnapshot);
        Assert.assertTrue(quotaPort.settledUsageSnapshot.hasTokenUsage());
        Assert.assertTrue(quotaPort.settledUsageSnapshot.estimated());
        Assert.assertEquals("REACT_EXECUTE_ERROR", recorder.finishRecord.getErrorCode());
    }

    private static class RecordingAgentQuotaPort implements AgentQuotaPort {

        private String precheckUserId;
        private String precheckTaskType;
        private int settleCount;
        private String settleUserId;
        private String settleSessionId;
        private String settleTaskConsumeBizId;
        private String settleTaskType;
        private AgentTokenUsageAccumulator.UsageSnapshot settledUsageSnapshot;

        @Override
        public void precheck(String userId, String taskType) {
            this.precheckUserId = userId;
            this.precheckTaskType = taskType;
        }

        @Override
        public void settle(String userId,
                           String sessionId,
                           String taskConsumeBizId,
                           String taskType,
                           AgentTokenUsageAccumulator.UsageSnapshot usageSnapshot) {
            this.settleCount++;
            this.settleUserId = userId;
            this.settleSessionId = sessionId;
            this.settleTaskConsumeBizId = taskConsumeBizId;
            this.settleTaskType = taskType;
            this.settledUsageSnapshot = usageSnapshot;
        }
    }

    private static class RecordingExecutionRecorder implements AgentExecutionRecorder {

        private DialogueRunStartRecord startRecord;
        private DialogueRunFinishRecord finishRecord;

        @Override
        public Long createRun(DialogueRunStartRecord record) {
            this.startRecord = record;
            return 10001L;
        }

        @Override
        public void finishRun(DialogueRunFinishRecord record) {
            this.finishRecord = record;
        }

        @Override
        public Long createLlmInvocation(LlmInvocationStartRecord record) {
            return 20001L;
        }

        @Override
        public void finishLlmInvocation(LlmInvocationFinishRecord record) {
        }

        @Override
        public Map<String, Long> createToolInvocations(ToolInvocationBatchStartRecord record) {
            return Map.of();
        }

        @Override
        public void finishToolInvocation(ToolInvocationFinishRecord record) {
        }

        @Override
        public void recordArtifacts(List<ArtifactRecordCommand> records) {
        }

        @Override
        public void recordArtifactsOrThrow(List<ArtifactRecordCommand> records) {
        }
    }
}
