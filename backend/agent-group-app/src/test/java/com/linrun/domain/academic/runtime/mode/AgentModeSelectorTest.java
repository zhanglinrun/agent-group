package com.linrun.domain.academic.runtime.mode;

import com.linrun.domain.academic.runtime.reasoning.AcademicAgentReasoningService;
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

        assertEquals("Flow", result.getExecutionMode());
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
    void testTaskAnalysisIncluded() {
        AgentModeSelector selector = new AgentModeSelector();
        String question = "深入分析区块链技术的应用场景";

        AgentModeSelector.ModeSelectionResult result = selector.selectMode(
                question,
                AgentModeSelector.ModeSelectionContext.empty()
        );

        AcademicAgentReasoningService.TaskAnalysisResult analysis = result.getTaskAnalysis();
        assertNotNull(analysis);
        assertEquals("深度分析", analysis.getTaskType());
        assertTrue(analysis.getEstimatedSteps() >= 3);
    }
}
