package com.linrun.domain.agent.conversation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.agent.conversation.model.AgentPlan;
import com.linrun.domain.agent.conversation.model.GuideDecisionResult;
import com.linrun.domain.agent.conversation.model.GuideIntentType;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.quality.model.GuideEvaluationCase;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlannerServiceTest {

    @Test
    void shouldFillRuntimeGoodsIdForGroupTrialTool() {
        AgentPlannerService service = new AgentPlannerService(
                new GuideDecisionService(null, null),
                new AgentToolRegistry());

        AgentPlan plan = service.plan("预算 2500 以内买学习平板，想知道能不能拼团");

        assertTrue(service.hasRuntimePlaceholder(plan));
        assertEquals("group-trial-v1", plan.getTools().stream()
                .filter(tool -> AgentToolRegistry.GROUP_TRIAL.equals(tool.getName()))
                .findFirst()
                .orElseThrow()
                .getToolVersion());
        assertEquals("HIGH", plan.getTools().stream()
                .filter(tool -> AgentToolRegistry.GROUP_TRIAL.equals(tool.getName()))
                .findFirst()
                .orElseThrow()
                .getRiskLevel());
        assertFalse(plan.getSkills().isEmpty());
        assertEquals("clarify", plan.getExecutionStages().get(0).getStage());
        assertTrue(plan.getCritique().contains("交易"));

        GuideProduct product = new GuideProduct();
        product.setGoodsId("G10001");
        GuideDecisionResult decisionResult = new GuideDecisionResult();
        decisionResult.setProduct(product);

        service.fillRuntimeArguments(plan, decisionResult);

        assertFalse(service.hasRuntimePlaceholder(plan));
        assertEquals("G10001", plan.getTools().stream()
                .filter(tool -> AgentToolRegistry.GROUP_TRIAL.equals(tool.getName()))
                .findFirst()
                .orElseThrow()
                .getArguments()
                .get("goodsId"));
    }

    @Test
    void shouldUseGroupTrialForPaymentAndTradeRuleQuestion() {
        AgentPlannerService service = new AgentPlannerService(
                new GuideDecisionService(null, null),
                new AgentToolRegistry());

        AgentPlan plan = service.plan("拼团支付成功以后订单就算已成团了吗？");

        assertEquals(GuideIntentType.GROUP_RULE, plan.getIntent());
        assertEquals(
                java.util.List.of(AgentToolRegistry.KNOWLEDGE_SEARCH, AgentToolRegistry.GUIDE_RECOMMEND, AgentToolRegistry.GROUP_TRIAL),
                plan.toolNames());
    }

    @Test
    void shouldKeepConcreteOrderQueryOnOrderStatusToolOnly() {
        AgentPlannerService service = new AgentPlannerService(
                new GuideDecisionService(null, null),
                new AgentToolRegistry());

        AgentPlan plan = service.plan("查一下订单 O10001 的支付状态。");

        assertEquals(GuideIntentType.ORDER_QUERY, plan.getIntent());
        assertEquals(java.util.List.of(AgentToolRegistry.ORDER_STATUS), plan.toolNames());
    }

    @Test
    void shouldNotTrialGroupForPurePerformanceBoundaryQuestion() {
        AgentPlannerService service = new AgentPlannerService(
                new GuideDecisionService(null, null),
                new AgentToolRegistry());

        AgentPlan plan = service.plan("标准版适合长期剪视频和大型游戏吗？");

        assertEquals(GuideIntentType.PRODUCT_RECOMMEND, plan.getIntent());
        assertFalse(plan.hasTool(AgentToolRegistry.GROUP_TRIAL));
    }

    @Test
    void shouldMatchToolPlanForSampleEvaluationCases() throws Exception {
        AgentPlannerService service = new AgentPlannerService(
                new GuideDecisionService(null, null),
                new AgentToolRegistry());
        Path caseFile = Path.of("..", "..", "docs", "sample-knowledge", "evaluation-cases.json");
        List<GuideEvaluationCase> cases = new ObjectMapper().readValue(
                Files.readString(caseFile),
                new TypeReference<>() {
                });

        for (GuideEvaluationCase evaluationCase : cases) {
            AgentPlan plan = service.plan(evaluationCase.getQuestion());
            assertEquals(evaluationCase.getExpectedIntentType(), plan.getIntent(), evaluationCase.getCaseId());
            assertEquals(evaluationCase.getExpectedToolOrder(), plan.toolNames(), evaluationCase.getCaseId());
        }
    }
}
