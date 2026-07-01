package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.agent.AgentPlanLifecycleService;
import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolCallResult;
import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlanningToolRuntimeTest {

    @Test
    void shouldCreateMarkAndReplanThroughToolRuntime() {
        AgentPlanningToolRuntime planningTool = new AgentPlanningToolRuntime();
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.registerStructured(AgentPlanningToolRuntime.definition(), planningTool::call);

        AgentToolCallResult createResult = registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.PLANNING)
                .arguments(Map.of(
                        "command", "create",
                        "title", "论文实验研究",
                        "steps", List.of("读取论文摘要", "检查实验指标", "生成报告")))
                .build());

        assertTrue(createResult.isSuccess());
        assertEquals("论文实验研究", createResult.getResult().get("title"));
        assertEquals(AgentPlanLifecycleService.STATUS_IN_PROGRESS,
                planningTool.currentPlan().getSteps().getFirst().getStatus());

        registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.PLANNING)
                .arguments(Map.of(
                        "command", "mark_step",
                        "stepIndex", 0,
                        "status", AgentPlanLifecycleService.STATUS_COMPLETED,
                        "note", "论文摘要已确认"))
                .build());

        AgentToolCallResult updateResult = registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.PLANNING)
                .arguments(Map.of(
                        "command", "update",
                        "title", "实验指标复查",
                        "steps", List.of("补充消融实验对比", "输出实验分析报告")))
                .build());

        assertTrue(updateResult.isSuccess());
        assertEquals("读取论文摘要", planningTool.currentPlan().getSteps().getFirst().getInstruction());
        assertEquals("补充消融实验对比", planningTool.currentPlan().getSteps().get(1).getInstruction());
    }

    @Test
    void shouldExposeSequentialFlowStagesForCreatedPlan() {
        AgentPlanningToolRuntime planningTool = new AgentPlanningToolRuntime();
        planningTool.call(AgentToolCallCommand.builder(AgentToolOutputNames.PLANNING)
                .arguments(Map.of(
                        "command", "create",
                        "title", "顺序计划",
                        "steps", List.of("第一步", "第二步")))
                .build());

        AgentToolCallResult result = AgentToolRuntimeRegistryHolder.call(planningTool,
                Map.of("command", "flow"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) metadata.get("flowStages");

        assertEquals(2, stages.size());
        assertEquals(0, stages.getFirst().get("stageIndex"));
    }

    private static final class AgentToolRuntimeRegistryHolder {

        private static AgentToolCallResult call(AgentPlanningToolRuntime planningTool,
                                                   Map<String, Object> arguments) {
            AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
            registry.registerStructured(AgentPlanningToolRuntime.definition(), planningTool::call);
            return registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.PLANNING)
                    .arguments(arguments)
                    .build());
        }
    }
}















