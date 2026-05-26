package com.linrun.domain.conversation.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuideDecisionSnapshotTest {

    @Test
    void shouldUseDatabaseSafeDefaultsWhenActivityMissing() {
        GuideProduct product = new GuideProduct();
        product.setGoodsId("G10001");
        product.setGoodsName("轻薄学习平板标准版");
        product.setOriginPrice(new BigDecimal("2399.00"));
        product.setGroupPrice(new BigDecimal("2399.00"));
        GuideDecisionResult decisionResult = new GuideDecisionResult();
        decisionResult.setProduct(product);

        GuideDecisionSnapshot snapshot = GuideDecisionSnapshot.capture(
                "S10001", "R10001", "U10001", "推荐平板", decisionResult, List.of(), new AgentPlan());

        assertEquals("", snapshot.getActivityId());
        assertEquals("G10001", snapshot.getGoodsId());
        assertEquals(new BigDecimal("2399.00"), snapshot.getOriginAmount());
    }
}
