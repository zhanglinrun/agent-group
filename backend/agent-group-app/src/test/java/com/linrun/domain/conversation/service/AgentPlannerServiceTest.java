package com.linrun.domain.conversation.service;

import com.linrun.domain.conversation.model.AgentPlan;
import com.linrun.domain.conversation.model.GuideDecisionResult;
import com.linrun.domain.conversation.model.GuideProduct;
import org.junit.jupiter.api.Test;

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
}
