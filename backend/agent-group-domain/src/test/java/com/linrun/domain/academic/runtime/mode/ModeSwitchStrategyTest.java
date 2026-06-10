package com.linrun.domain.academic.runtime.mode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模式切换策略测试。
 */
class ModeSwitchStrategyTest {

    private ExecutionModeRegistry registry;
    private ModeSwitchStrategy strategy;

    @BeforeEach
    void setUp() {
        registry = new ExecutionModeRegistry();

        registry.register(new TestReActMode());
        registry.register(new TestPlanExecuteMode());
        registry.register(new TestFlowMode());
        registry.register(new TestSkillMode());

        strategy = new ModeSwitchStrategy(registry);
    }

    @Test
    void testSelectMode_WithAttachment() {
        var mode = strategy.selectMode("分析这个文件", List.of(new Object()));

        assertEquals("react", mode.modeName());
    }

    @Test
    void testSelectMode_DeepResearch() {
        var mode = strategy.selectMode("深度研究区块链技术的发展趋势", List.of());

        assertEquals("plan-execute", mode.modeName());
    }

    @Test
    void testSelectMode_PPT() {
        var mode = strategy.selectMode("生成一个关于 AI 的 PPT", List.of());

        assertEquals("flow", mode.modeName());
    }

    @Test
    void testSelectMode_Skill() {
        var mode = strategy.selectMode("/执行技能 数据分析", List.of());

        assertEquals("skill-sop", mode.modeName());
    }

    @Test
    void testSelectMode_Default() {
        var mode = strategy.selectMode("今天天气怎么样？", List.of());

        assertEquals("react", mode.modeName());
    }

    static class TestReActMode implements AgentExecutionMode {
        public String modeName() { return "react"; }
        public String description() { return "ReAct"; }
        public boolean canHandle(ExecutionContext ctx) { return true; }
        public ExecutionResult execute(ExecutionContext ctx) { return ExecutionResult.success("ok"); }
        public List<String> requiredTools() { return List.of(); }
    }

    static class TestPlanExecuteMode implements AgentExecutionMode {
        public String modeName() { return "plan-execute"; }
        public String description() { return "Plan-Execute"; }
        public boolean canHandle(ExecutionContext ctx) { return true; }
        public ExecutionResult execute(ExecutionContext ctx) { return ExecutionResult.success("ok"); }
        public List<String> requiredTools() { return List.of(); }
    }

    static class TestFlowMode implements AgentExecutionMode {
        public String modeName() { return "flow"; }
        public String description() { return "Flow"; }
        public boolean canHandle(ExecutionContext ctx) { return true; }
        public ExecutionResult execute(ExecutionContext ctx) { return ExecutionResult.success("ok"); }
        public List<String> requiredTools() { return List.of(); }
    }

    static class TestSkillMode implements AgentExecutionMode {
        public String modeName() { return "skill-sop"; }
        public String description() { return "Skill-SOP"; }
        public boolean canHandle(ExecutionContext ctx) { return true; }
        public ExecutionResult execute(ExecutionContext ctx) { return ExecutionResult.success("ok"); }
        public List<String> requiredTools() { return List.of(); }
    }
}
