package com.linrun.domain.academic.runtime.agent.skill;

import com.linrun.domain.academic.runtime.agent.AgentResponse;
import com.linrun.domain.academic.runtime.agent.AcademicAgentTaskManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能编排 Agent
 *
 * 职责：
 * 1. 识别用户请求中的技能调用需求
 * 2. 按顺序或并行编排多个技能
 * 3. 聚合技能执行结果
 */
@Service
public class SkillOrchestratorAgent {

    private final AcademicAgentTaskManager taskManager;
    private final SkillRegistry skillRegistry;

    @Autowired
    public SkillOrchestratorAgent(AcademicAgentTaskManager taskManager) {
        this.taskManager = taskManager;
        this.skillRegistry = new SkillRegistry();
    }

    /**
     * 执行技能编排任务
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
        AcademicAgentTaskManager.TaskInfo taskInfo = taskManager.registerTask(sessionId, sink, "SKILL_ORCHESTRATOR");
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
                sink.tryEmitNext(AgentResponse.error("技能执行失败: " + e.getMessage()));
                sink.tryEmitComplete();
            }
        }, "skill-orchestrator-" + sessionId).start();
    }

    private void doExecute(String sessionId, String query, Sinks.Many<String> sink) {
        // 1. 分析技能需求
        sink.tryEmitNext(AgentResponse.thinking("🔍 分析技能需求..."));
        List<String> requiredSkills = analyzeRequiredSkills(query);

        if (requiredSkills.isEmpty()) {
            sink.tryEmitNext(AgentResponse.text("未识别到需要调用的技能"));
            sink.tryEmitComplete();
            return;
        }

        sink.tryEmitNext(AgentResponse.thinking("📋 识别到技能：" + String.join(", ", requiredSkills)));

        // 2. 编排执行顺序
        sink.tryEmitNext(AgentResponse.thinking("🔧 编排执行顺序..."));

        // 3. 依次执行技能
        for (String skillName : requiredSkills) {
            sink.tryEmitNext(AgentResponse.thinking("▶️ 执行技能：" + skillName));
            executeSkill(skillName, query, sink);
        }

        // 4. 聚合结果
        sink.tryEmitNext(AgentResponse.text("\n✅ 所有技能执行完成"));
        sink.tryEmitComplete();
    }

    /**
     * 分析所需技能
     */
    private List<String> analyzeRequiredSkills(String query) {
        List<String> skills = new ArrayList<>();

        // TODO: 实际应该使用更智能的方式识别技能需求
        if (query.contains("搜索") || query.contains("查找")) {
            skills.add("web_search");
        }
        if (query.contains("文档") || query.contains("文件")) {
            skills.add("document_reader");
        }
        if (query.contains("分析") || query.contains("总结")) {
            skills.add("text_analyzer");
        }
        if (query.contains("翻译")) {
            skills.add("translator");
        }

        return skills;
    }

    /**
     * 执行单个技能
     */
    private void executeSkill(String skillName, String input, Sinks.Many<String> sink) {
        try {
            // TODO: 实际调用技能执行
            String result = "技能 " + skillName + " 执行结果（示例）";
            sink.tryEmitNext(AgentResponse.toolResult(skillName, result));
        } catch (Exception e) {
            sink.tryEmitNext(AgentResponse.error("技能 " + skillName + " 执行失败"));
        }
    }

    /**
     * 技能注册表（简化版）
     */
    private static class SkillRegistry {
        // TODO: 实际应该从配置或数据库加载技能列表
        private final List<String> availableSkills = List.of(
            "web_search",
            "document_reader",
            "text_analyzer",
            "translator",
            "calculator"
        );

        public boolean hasSkill(String skillName) {
            return availableSkills.contains(skillName);
        }

        public List<String> listAllSkills() {
            return new ArrayList<>(availableSkills);
        }
    }
}
