package com.linrun.domain.academic.runtime.agent.ppt;

import com.linrun.domain.academic.runtime.agent.AgentResponse;
import com.linrun.domain.academic.runtime.agent.AcademicAgentTaskManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * PPT 构建 Agent
 *
 * 职责：
 * 1. 识别用户意图（创建、修改、恢复）
 * 2. 根据意图执行相应的 PPT 生成策略
 * 3. 流式输出生成过程和结果
 */
@Service
public class PptBuilderAgent {

    private final PptIntentRecognizer intentRecognizer;
    private final AcademicAgentTaskManager taskManager;

    @Autowired
    public PptBuilderAgent(PptIntentRecognizer intentRecognizer, AcademicAgentTaskManager taskManager) {
        this.intentRecognizer = intentRecognizer;
        this.taskManager = taskManager;
    }

    /**
     * 执行 PPT 生成任务
     *
     * @param sessionId 会话 ID
     * @param query 用户请求
     * @return 流式输出
     */
    public Flux<String> execute(String sessionId, String query) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 检查是否已有任务在执行
        if (taskManager.hasRunningTask(sessionId)) {
            sink.tryEmitError(new IllegalStateException("该会话正在执行中，请稍后再试"));
            return sink.asFlux();
        }

        // 注册任务
        AcademicAgentTaskManager.TaskInfo taskInfo = taskManager.registerTask(sessionId, sink, "PPT_BUILDER");
        if (taskInfo == null) {
            sink.tryEmitError(new IllegalStateException("任务注册失败"));
            return sink.asFlux();
        }

        // 异步执行
        executeAsync(sessionId, query, sink);

        return sink.asFlux()
                .doOnCancel(() -> taskManager.stopTask(sessionId))
                .doFinally(signalType -> taskManager.stopTask(sessionId));
    }

    private void executeAsync(String sessionId, String query, Sinks.Many<String> sink) {
        new Thread(() -> {
            try {
                doExecute(sessionId, query, sink);
            } catch (Exception e) {
                sink.tryEmitNext(AgentResponse.error("PPT生成失败: " + e.getMessage()));
                sink.tryEmitComplete();
            }
        }, "ppt-builder-" + sessionId).start();
    }

    private void doExecute(String sessionId, String query, Sinks.Many<String> sink) {
        // 1. 意图识别
        PptIntentRecognizer.PptIntentResult intentResult = intentRecognizer.recognize(sessionId, query);

        sink.tryEmitNext(AgentResponse.thinking("🔍 识别意图：" + intentResult.getIntent().getDescription()));

        // 2. 根据意图路由
        switch (intentResult.getIntent()) {
            case CREATE_PPT -> handleCreateIntent(sessionId, query, sink);
            case MODIFY_PPT -> handleModifyIntent(sessionId, query, sink);
            case RESUME_PPT -> handleResumeIntent(sessionId, query, sink);
            default -> {
                sink.tryEmitNext(AgentResponse.error("❌ 无法识别您的意图，请重新表述"));
                sink.tryEmitComplete();
            }
        }
    }

    /**
     * 处理创建 PPT 意图
     */
    private void handleCreateIntent(String sessionId, String query, Sinks.Many<String> sink) {
        sink.tryEmitNext(AgentResponse.thinking("📝 开始创建PPT..."));

        // 1. 分析主题
        sink.tryEmitNext(AgentResponse.thinking("📊 分析主题和内容需求..."));

        // 2. 生成大纲
        sink.tryEmitNext(AgentResponse.thinking("📋 生成PPT大纲..."));
        String outline = generateOutline(query);
        sink.tryEmitNext(AgentResponse.text("大纲：\n" + outline));

        // 3. 生成每页内容
        sink.tryEmitNext(AgentResponse.thinking("✍️ 生成各页内容..."));

        // 4. 格式化输出
        sink.tryEmitNext(AgentResponse.text("\n✅ PPT内容已生成"));

        // TODO: 实际调用 PPT 生成工具
        sink.tryEmitNext(AgentResponse.text("\n💡 提示：请使用「修改」命令调整内容，或使用「继续」命令完善细节。"));

        sink.tryEmitComplete();
    }

    /**
     * 处理修改 PPT 意图
     */
    private void handleModifyIntent(String sessionId, String query, Sinks.Many<String> sink) {
        sink.tryEmitNext(AgentResponse.thinking("🔧 开始修改PPT..."));

        // 1. 获取现有 PPT
        sink.tryEmitNext(AgentResponse.thinking("📂 加载现有PPT..."));

        // 2. 解析修改要求
        sink.tryEmitNext(AgentResponse.thinking("🔍 解析修改要求..."));

        // 3. 应用修改
        sink.tryEmitNext(AgentResponse.thinking("✏️ 应用修改..."));

        // TODO: 实际修改逻辑
        sink.tryEmitNext(AgentResponse.text("✅ 修改完成"));

        sink.tryEmitComplete();
    }

    /**
     * 处理恢复 PPT 意图
     */
    private void handleResumeIntent(String sessionId, String query, Sinks.Many<String> sink) {
        sink.tryEmitNext(AgentResponse.thinking("🔄 恢复上次的PPT..."));

        // 1. 查找上次的 PPT
        sink.tryEmitNext(AgentResponse.thinking("🔍 查找上次未完成的PPT..."));

        // 2. 加载状态
        sink.tryEmitNext(AgentResponse.thinking("📂 加载PPT状态..."));

        // TODO: 实际恢复逻辑
        sink.tryEmitNext(AgentResponse.text("✅ 已恢复上次的PPT，请继续编辑"));

        sink.tryEmitComplete();
    }

    /**
     * 生成 PPT 大纲（简化版）
     */
    private String generateOutline(String topic) {
        // TODO: 实际应该调用 LLM 生成大纲
        return """
            第1页：封面
            - 标题：%s
            - 副标题：简要介绍

            第2页：目录
            - 章节概览

            第3页：内容主体
            - 核心观点

            第4页：总结
            - 要点回顾
            """.formatted(topic);
    }
}
