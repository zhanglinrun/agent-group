package com.linrun.domain.academic.runtime.agent;

import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.runtime.diagnosis.AgentDiagnosisService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Agent 流式服务
 *
 * 职责：
 * 1. 管理流式响应的生命周期
 * 2. 协调各个组件（执行引擎、诊断、投影器）
 * 3. 处理执行事件并转换为流式输出
 */
@Service
public class AcademicAgentStreamService {

    private final AcademicAgentFlowExecutionService executionService;
    private final AcademicAgentResponseProjector projector;
    private final AgentTaskManager taskManager;
    private final AgentDiagnosisService diagnosisService;

    public AcademicAgentStreamService(AcademicAgentFlowExecutionService executionService) {
        this.executionService = executionService;
        this.projector = new AcademicAgentResponseProjector();
        this.taskManager = new AgentTaskManager();
        this.diagnosisService = new AgentDiagnosisService();
    }

    /**
     * 执行 Agent 并返回流式响应
     *
     * @param sessionId 会话 ID
     * @param query 用户查询
     * @param agentType Agent 类型
     * @return 流式响应
     */
    public Flux<String> executeAndStream(String sessionId, String query, String agentType) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 检查是否有正在运行的任务
        if (taskManager.hasRunningTask(sessionId)) {
            sink.tryEmitError(new IllegalStateException("该会话正在执行中，请稍后再试"));
            return sink.asFlux();
        }

        // 注册任务
        AgentTaskManager.TaskInfo taskInfo = taskManager.registerTask(sessionId, sink, agentType);
        if (taskInfo == null) {
            sink.tryEmitError(new IllegalStateException("任务注册失败"));
            return sink.asFlux();
        }

        // 异步执行
        executeAsync(sessionId, query, agentType, sink);

        return sink.asFlux()
                .doOnCancel(() -> taskManager.stopTask(sessionId))
                .doFinally(signalType -> taskManager.stopTask(sessionId));
    }

    /**
     * 停止会话的任务
     */
    public void stopTask(String sessionId) {
        taskManager.stopTask(sessionId);
    }

    /**
     * 取消会话的任务
     */
    public void cancelTask(String sessionId, String reason) {
        taskManager.cancelTask(sessionId, reason);
    }

    /**
     * 获取正在运行的任务数量
     */
    public int getRunningTaskCount() {
        return taskManager.getRunningTaskCount();
    }

    // ========== 私有方法 ==========

    private void executeAsync(String sessionId, String query, String agentType, Sinks.Many<String> sink) {
        new Thread(() -> {
            try {
                doExecute(sessionId, query, agentType, sink);
            } catch (Exception e) {
                sink.tryEmitNext(projector.projectError("EXEC_ERROR", e.getMessage()));
                sink.tryEmitComplete();
            }
        }, "agent-stream-" + sessionId).start();
    }

    private void doExecute(String sessionId, String query, String agentType, Sinks.Many<String> sink) {
        LocalDateTime startTime = LocalDateTime.now();
        RunContext context = new RunContext();

        try {
            // 1. 发送运行开始事件
            AcademicAgentRun run = createRun(sessionId, query, agentType);
            sink.tryEmitNext(projector.projectRunStart(run, sessionId));
            context.run = run;

            // 2. 执行 Agent 流程
            // TODO: 调用实际的执行引擎
            // AcademicAgentFlowExecutionResult result = executionService.execute(...);

            // 3. 处理执行结果
            // TODO: 根据执行结果发送相应的流式事件

            // 4. 发送诊断报告
            emitDiagnosisIfNeeded(context, sink, startTime);

            // 5. 发送完成事件
            sink.tryEmitNext(projector.projectRunDone(context.run));
            sink.tryEmitComplete();

        } catch (Exception e) {
            sink.tryEmitNext(projector.projectError("EXEC_ERROR", e.getMessage()));
            sink.tryEmitComplete();
        }
    }

    private AcademicAgentRun createRun(String sessionId, String query, String agentType) {
        // TODO: 实际创建 run 对象
        AcademicAgentRun run = new AcademicAgentRun();
        run.setRunId("R" + System.currentTimeMillis());
        run.setSessionId(sessionId);
        run.setStatus("RUNNING");
        return run;
    }

    private void emitDiagnosisIfNeeded(RunContext context, Sinks.Many<String> sink, LocalDateTime startTime) {
        long elapsedMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();

        AgentDiagnosisService.DiagnosisReport report = diagnosisService.diagnose(
                new AgentDiagnosisService.AgentRunContext(
                        context.run.getRunId(),
                        elapsedMs,
                        context.failedToolCount,
                        context.quotaConsumed,
                        context.replanCount,
                        false,
                        null
                )
        );

        if (report.getLevel() != AgentDiagnosisService.DiagnosisLevel.OK) {
            sink.tryEmitNext(projector.projectDiagnosis(
                    report,
                    elapsedMs,
                    context.toolCallCount,
                    context.failedToolCount,
                    context.quotaConsumed,
                    context.replanCount
            ));
        }
    }

    /**
     * 运行上下文（累积运行时信息）
     */
    private static class RunContext {
        AcademicAgentRun run;
        int toolCallCount = 0;
        int failedToolCount = 0;
        double quotaConsumed = 0.0;
        int replanCount = 0;
    }
}
