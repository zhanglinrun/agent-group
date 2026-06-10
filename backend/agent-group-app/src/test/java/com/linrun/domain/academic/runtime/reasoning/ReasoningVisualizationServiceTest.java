package com.linrun.domain.academic.runtime.reasoning;

import com.linrun.domain.academic.runtime.mode.AgentModeSelector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReasoningVisualizationServiceTest {

    @Test
    void testGenerateReasoningVisualization() {
        ReasoningVisualizationService service = new ReasoningVisualizationService();
        AgentModeSelector selector = new AgentModeSelector();

        String question = "研究人工智能的伦理问题";
        AgentModeSelector.ModeSelectionResult result = selector.selectMode(
                question,
                AgentModeSelector.ModeSelectionContext.empty()
        );

        String visualization = service.generateReasoningVisualization(question, result);

        assertNotNull(visualization);
        assertTrue(visualization.contains("任务分析"));
        assertTrue(visualization.contains("执行策略"));
        assertTrue(visualization.contains("Plan-Execute"));
    }

    @Test
    void testGenerateQuickReasoningHint() {
        ReasoningVisualizationService service = new ReasoningVisualizationService();
        AgentModeSelector selector = new AgentModeSelector();

        String question = "介绍一下Java的特点";
        AgentModeSelector.ModeSelectionResult result = selector.selectMode(
                question,
                AgentModeSelector.ModeSelectionContext.empty()
        );

        String hint = service.generateQuickReasoningHint(result);

        assertNotNull(hint);
        assertTrue(hint.contains("ReAct"));
        assertTrue(hint.contains("模式"));
    }

    @Test
    void testGeneratePlanPreview() {
        ReasoningVisualizationService service = new ReasoningVisualizationService();

        String question = "深入研究量子计算的发展历史和未来应用";
        String preview = service.generatePlanPreview(question);

        assertNotNull(preview);
        assertTrue(preview.contains("计划预览"));
        assertTrue(preview.contains("需求澄清"));
        assertTrue(preview.contains("资料检索"));
        assertTrue(preview.contains("综合分析"));
    }
}
