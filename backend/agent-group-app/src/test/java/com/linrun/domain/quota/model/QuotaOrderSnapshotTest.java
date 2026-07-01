package com.linrun.domain.quota.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuotaOrderSnapshotTest {

    @Test
    void shouldUseDatabaseSafeDefaultsWhenActivityMissing() {
        QuotaProduct product = new QuotaProduct();
        product.setGoodsId("G10001");
        product.setGoodsName("基础额度包");
        product.setOriginPrice(new BigDecimal("2399.00"));
        product.setGroupPrice(new BigDecimal("2399.00"));

        QuotaOrderSnapshot snapshot = QuotaOrderSnapshot.captureQuote(
                "S10001", "R10001", "U10001", "推荐额度包", product);

        assertEquals("", snapshot.getActivityId());
        assertEquals("G10001", snapshot.getGoodsId());
        assertEquals(new BigDecimal("2399.00"), snapshot.getOriginAmount());
        assertEquals("", snapshot.getReferenceIds());
        assertEquals("", snapshot.getToolNames());
    }
}















