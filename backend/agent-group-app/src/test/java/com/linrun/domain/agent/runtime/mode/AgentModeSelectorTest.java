package com.linrun.domain.agent.runtime.mode;

import com.linrun.domain.agent.runtime.reasoning.AgentReasoningService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentModeSelectorTest {

    @Test
    void testSelectMode_DeepResearchQuestion() {
        AgentModeSelector selector = new AgentModeSelector();
        String question = "请深入研究一下人工智能在医疗领域的应用现状和未来发展趋势";

        AgentModeSelector.ModeSelectionResult result = selector.selectMode(
                question,
                AgentModeSelector.ModeSelectionContext.empty()
        );

        assertEquals("Plan-Execute", result.getExecutionMode());
        assertEquals("deep", result.getAgentType());
        assertEquals("plan-execute", result.getModeFamily());
        assertTrue(result.getReason().contains("规划"));
    }

    @Test
    void testSelectMode_PPTQuestion() {
        AgentModeSelector selector = new AgentModeSelector();
        String question = "帮我做一个关于机器学习的PPT";

        AgentModeSelector.ModeSelectionResult result = selector.selectMode(
                question,
                AgentModeSelector.ModeSelectionContext.empty()
        );

        assertEquals("PPT Workflow", result.getExecutionMode());
        assertEquals("ppt", result.getAgentType());
    }

    @Test
    void testSelectMode_FileAttachment() {
        AgentModeSelector selector = new AgentModeSelector();
        String question = "这个文档讲了什么";

        AgentModeSelector.ModeSelectionResult result = selector.selectMode(
                question,
                AgentModeSelector.ModeSelectionContext.withAttachment("file")
        );

        assertEquals("ReAct", result.getExecutionMode());
        assertEquals("file", result.getAgentType());
    }

    @Test
    void testSelectMode_ExplicitMode() {
        AgentModeSelector selector = new AgentModeSelector();
        String question = "介绍一下Spring Boot";

        AgentModeSelector.ModeSelectionResult result = selector.selectMode(
                question,
                AgentModeSelector.ModeSelectionContext.simple("deep")
        );

        assertEquals("Plan-Execute", result.getExecutionMode());
        assertEquals("deep", result.getAgentType());
    }

    @Test
    void testSelectMode_ExplicitModeWinsOverAttachment() {
        AgentModeSelector selector = new AgentModeSelector();

        AgentModeSelector.ModeSelectionResult result = selector.selectMode(
                "根据这个文件生成一份答辩 PPT",
                new AgentModeSelector.ModeSelectionContext(true, "file", true, "ppt")
        );

        assertEquals("PPT Workflow", result.getExecutionMode());
        assertEquals("ppt", result.getAgentType());
        assertEquals("ppt-workflow", result.getModeFamily());
    }

    @Test
    void testSelectMode_SkillSopAlias() {
        AgentModeSelector selector = new AgentModeSelector();

        AgentModeSelector.ModeSelectionResult result = selector.selectMode(
                "调用技能整理实验数据",
                AgentModeSelector.ModeSelectionContext.simple("skill-sop")
        );

        assertEquals("Skill Orchestration", result.getExecutionMode());
        assertEquals("skill", result.getAgentType());
        assertEquals("skill-orchestration", result.getModeFamily());
    }

    @Test
    void testSelectMode_NullContextUsesDefaultContext() {
        AgentModeSelector selector = new AgentModeSelector();

        AgentModeSelector.ModeSelectionResult result = selector.selectMode("你好", null);

        assertEquals("ReAct", result.getExecutionMode());
        assertEquals("chat", result.getAgentType());
    }

    @Test
    void testSelectMode_SimpleChat() {
        AgentModeSelector selector = new AgentModeSelector();
        String question = "你好";

        AgentModeSelector.ModeSelectionResult result = selector.selectMode(
                question,
                AgentModeSelector.ModeSelectionContext.empty()
        );

        assertEquals("ReAct", result.getExecutionMode());
        assertEquals("chat", result.getAgentType());
    }

    @Test
    void shouldKeepExplicitSearchQuestionsInSearchReact() {
        AgentModeSelector selector = new AgentModeSelector();

        AgentModeSelector.ModeSelectionResult latestResult = selector.selectMode(
                "帮我查找 2025 年以来关于向量数据库的综述",
                AgentModeSelector.ModeSelectionContext.empty()
        );
        AgentModeSelector.ModeSelectionResult comparisonResult = selector.selectMode(
                "检索 pgvector 与 Milvus 的性能对比资料",
                AgentModeSelector.ModeSelectionContext.empty()
        );

        assertEquals("ReAct", latestResult.getExecutionMode());
        assertEquals("search", latestResult.getAgentType());
        assertEquals("ReAct", comparisonResult.getExecutionMode());
        assertEquals("search", comparisonResult.getAgentType());
    }

    @Test
    void shouldSelectPlanExecuteForAgentResearchPlanning() {
        AgentModeSelector selector = new AgentModeSelector();

        AgentModeSelector.ModeSelectionResult result = selector.selectMode(
                "调研近三年大语言模型推理加速的主要技术路线",
                AgentModeSelector.ModeSelectionContext.empty()
        );

        assertEquals("Plan-Execute", result.getExecutionMode());
        assertEquals("deep", result.getAgentType());
        assertTrue(result.getTaskAnalysis().needsMultipleSources());
    }

    @Test
    void shouldKeepConceptExplanationInReact() {
        AgentModeSelector selector = new AgentModeSelector();

        AgentModeSelector.ModeSelectionResult result = selector.selectMode(
                "请介绍一下 Spring Boot 的自动装配机制",
                AgentModeSelector.ModeSelectionContext.empty()
        );

        assertEquals("ReAct", result.getExecutionMode());
        assertEquals("chat", result.getAgentType());
    }

    @Test
    void testTaskAnalysisIncluded() {
        AgentModeSelector selector = new AgentModeSelector();
        String question = "深入分析区块链技术的应用场景";

        AgentModeSelector.ModeSelectionResult result = selector.selectMode(
                question,
                AgentModeSelector.ModeSelectionContext.empty()
        );

        AgentReasoningService.TaskAnalysisResult analysis = result.getTaskAnalysis();
        assertNotNull(analysis);
        assertEquals("深度分析", analysis.getTaskType());
        assertTrue(analysis.getEstimatedSteps() >= 3);
    }
}
