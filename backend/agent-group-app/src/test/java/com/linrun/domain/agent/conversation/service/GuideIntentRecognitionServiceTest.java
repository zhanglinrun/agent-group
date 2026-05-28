package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.model.GuideIntent;
import com.linrun.domain.agent.conversation.model.GuideIntentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideIntentRecognitionServiceTest {

    @Test
    void shouldExtractStructuredOrderAndGoodsEntities() {
        GuideIntent intent = new GuideIntentRecognitionService()
                .recognize("帮我查一下订单 O10001 的退款状态，商品是 G10002");

        assertEquals(GuideIntentType.ORDER_QUERY, intent.getIntentType());
        assertEquals("O10001", intent.getOrderId());
        assertEquals("G10002", intent.getGoodsId());
        assertTrue(intent.getEntities().contains("orderId:O10001"));
        assertTrue(intent.getEntities().contains("goodsId:G10002"));
    }

    @Test
    void shouldExtractBudgetScenarioAndCompareIntent() {
        GuideIntent intent = new GuideIntentRecognitionService()
                .recognize("我是研究生，预算 3500 以内，想剪视频和绘图，标准版和高配版怎么选？");

        assertEquals(GuideIntentType.PRODUCT_COMPARE, intent.getIntentType());
        assertEquals("学生", intent.getUserIdentity());
        assertEquals(new BigDecimal("3500"), intent.getBudgetUpperLimit());
        assertTrue(intent.isPerformanceSensitive());
        assertTrue(intent.getUsageScenarios().contains("创作应用"));
        assertTrue(intent.getEntities().contains("budgetUpperLimit:3500"));
    }
}
