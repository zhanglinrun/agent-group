package com.linrun.domain.agent.runtime.reasoning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 推理服务测试。
 */
class AgentReasoningServiceTest {

    private final AgentReasoningService service = new AgentReasoningService();

    @Test
    void testAnalyzeTask_DeepResearch() {
        String query = "请深度研究一下人工智能在医疗领域的应用现状和未来发展趋势";

        var result = service.analyzeTask(query);

        assertEquals("深度分析", result.getTaskType());
        assertTrue(result.getEstimatedSteps() >= 3);
        assertEquals("困难", result.getDifficulty());
    }

    @Test
    void testAnalyzeTask_SimpleQuery() {
        String query = "什么是 AI？";

        var result = service.analyzeTask(query);

        assertEquals(2, result.getEstimatedSteps());
        assertEquals("简单", result.getDifficulty());
    }

    @Test
    void testAnalyzeTask_ComparisonTask() {
        String query = "比较 React 和 Vue 的优缺点";

        var result = service.analyzeTask(query);

        assertTrue(result.needsMultipleSources());
    }

    @Test
    void testAnalyzeTask_EmptyQuery() {
        var result = service.analyzeTask("");

        assertEquals("未知", result.getTaskType());
    }
}
