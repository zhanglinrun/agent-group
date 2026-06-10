package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.agent.AcademicPlanLifecycleService;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicPlanningToolRuntimeTest {

    @Test
    void shouldCreateMarkAndReplanThroughToolRuntime() {
        AcademicPlanningToolRuntime planningTool = new AcademicPlanningToolRuntime();
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.registerStructured(AcademicPlanningToolRuntime.definition(), planningTool::call);

        AcademicToolCallResult createResult = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.PLANNING)
                .arguments(Map.of(
                        "command", "create",
                        "title", "论文实验研究",
                        "steps", List.of("读取论文摘要", "检查实验指标", "生成报告")))
                .build());

        assertTrue(createResult.isSuccess());
        assertEquals("论文实验研究", createResult.getResult().get("title"));
        assertEquals(AcademicPlanLifecycleService.STATUS_IN_PROGRESS,
                planningTool.currentPlan().getSteps().getFirst().getStatus());

        registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.PLANNING)
                .arguments(Map.of(
                        "command", "mark_step",
                        "stepIndex", 0,
                        "status", AcademicPlanLifecycleService.STATUS_COMPLETED,
                        "note", "论文摘要已确认"))
                .build());

        AcademicToolCallResult updateResult = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.PLANNING)
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
        AcademicPlanningToolRuntime planningTool = new AcademicPlanningToolRuntime();
        planningTool.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.PLANNING)
                .arguments(Map.of(
                        "command", "create",
                        "title", "顺序计划",
                        "steps", List.of("第一步", "第二步")))
                .build());

        AcademicToolCallResult result = AcademicToolRuntimeRegistryHolder.call(planningTool,
                Map.of("command", "flow"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.getResult().get("metadata");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) metadata.get("flowStages");

        assertEquals(2, stages.size());
        assertEquals(0, stages.getFirst().get("stageIndex"));
    }

    private static final class AcademicToolRuntimeRegistryHolder {

        private static AcademicToolCallResult call(AcademicPlanningToolRuntime planningTool,
                                                   Map<String, Object> arguments) {
            AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
            registry.registerStructured(AcademicPlanningToolRuntime.definition(), planningTool::call);
            return registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.PLANNING)
                    .arguments(arguments)
                    .build());
        }
    }
}















