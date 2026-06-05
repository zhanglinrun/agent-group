package com.linrun.domain.academic.runtime.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicAgentRunPlanFactoryTest {

    private final AcademicAgentRunPlanFactory planFactory = new AcademicAgentRunPlanFactory();
    private final AcademicAgentFlowProjector flowProjector = new AcademicAgentFlowProjector();

    @Test
    void shouldBuildDeepResearchPlanWithParallelEvidenceStage() {
        AcademicAgentPlan plan = planFactory.build("deep", true);
        List<AcademicAgentFlowStage> stages = flowProjector.buildRemainingStages(plan);

        assertEquals("深度研究", plan.getTitle());
        assertEquals(5, plan.getSteps().size());
        assertEquals(List.of("S1"), stages.get(0).stepIds());
        assertEquals(List.of("S2", "S3"), stages.get(1).stepIds());
        assertEquals(List.of("S4"), stages.get(2).stepIds());
        assertEquals(List.of("S5"), stages.get(3).stepIds());
        assertFalse(plan.getSteps().get(0).getAssignedAgent().isBlank());
    }

    @Test
    void shouldBuildDataPlanWithTradeValidationStage() {
        AcademicAgentPlan plan = planFactory.build("table-rag", false);
        List<String> instructions = plan.getSteps().stream()
                .map(AcademicPlanStep::getInstruction)
                .toList();

        assertEquals("数据问答", plan.getTitle());
        assertEquals(5, plan.getSteps().size());
        assertEquals("S3", plan.getSteps().get(3).getDependencies().getFirst());
        assertEquals("交易校验智能体", plan.getSteps().get(3).getAssignedAgent());
        assertTrue(instructions.stream().anyMatch(text -> text.contains("额度")
                && text.contains("订单")
                && text.contains("拼团")));
    }

    @Test
    void shouldBuildTradeAuditPlanWithParallelSettlementAndQuotaStages() {
        AcademicAgentPlan plan = planFactory.build("trade-audit", false);
        List<AcademicAgentFlowStage> stages = flowProjector.buildRemainingStages(plan);
        List<String> instructions = plan.getSteps().stream()
                .map(AcademicPlanStep::getInstruction)
                .toList();

        assertEquals("拼团交易审计", plan.getTitle());
        assertEquals(6, plan.getSteps().size());
        assertEquals(List.of("S1"), stages.get(0).stepIds());
        assertEquals(List.of("S2", "S3"), stages.get(1).stepIds());
        assertEquals(List.of("S4"), stages.get(2).stepIds());
        assertEquals(List.of("S5"), stages.get(3).stepIds());
        assertEquals(List.of("S6"), stages.get(4).stepIds());
        assertEquals("交易事实智能体", plan.getSteps().get(1).getAssignedAgent());
        assertEquals("拼团校验智能体", plan.getSteps().get(2).getAssignedAgent());
        assertTrue(instructions.stream().anyMatch(text -> text.contains("支付成功")
                && text.contains("等待成团")
                && text.contains("退款回滚")));
    }

    @Test
    void shouldNormalizeTradeAuditAliasesToTradePlan() {
        assertEquals("拼团交易审计", planFactory.build("group-trade", false).getTitle());
        assertEquals("拼团交易审计", planFactory.build("workspace-trade", false).getTitle());
    }
}
