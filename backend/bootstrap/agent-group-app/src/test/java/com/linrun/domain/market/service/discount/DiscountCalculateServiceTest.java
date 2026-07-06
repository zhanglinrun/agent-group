package com.linrun.domain.market.service.discount;

import com.linrun.domain.market.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.market.model.GroupBuyDiscount;
import com.linrun.domain.market.model.GroupBuyMarketSku;
import com.linrun.domain.market.model.SourceChannelSkuActivity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscountCalculateServiceTest {

    private static final BigDecimal ORIGINAL_PRICE = new BigDecimal("2399.00");

    @Test
    void shouldCalculateSupportedDiscountPlans() {
        GroupBuyMarketRepository repository = new FakeGroupBuyMarketRepository(true);

        assertEquals(new BigDecimal("2099.00"),
                new ZJCalculateService(repository).calculate("U10001", ORIGINAL_PRICE, discount(0, "ZJ", "300", null)));
        assertEquals(new BigDecimal("2389.00"),
                new MJCalculateService(repository).calculate("U10001", ORIGINAL_PRICE, discount(0, "MJ", "100,10", null)));
        assertEquals(new BigDecimal("1919.00"),
                new ZKCalculateService(repository).calculate("U10001", ORIGINAL_PRICE, discount(0, "ZK", "0.8", null)));
        assertEquals(new BigDecimal("1.99"),
                new NCalculateService(repository).calculate("U10001", ORIGINAL_PRICE, discount(0, "N", "1.99", null)));
    }

    @Test
    void shouldReturnOriginalPriceWhenTagDiscountUserIsOutsideCrowd() {
        GroupBuyMarketRepository repository = new FakeGroupBuyMarketRepository(false);
        GroupBuyDiscount discount = discount(1, "ZJ", "300", "TAG_VIP");

        BigDecimal payPrice = new ZJCalculateService(repository).calculate("U10001", ORIGINAL_PRICE, discount);

        assertEquals(new BigDecimal("2399.00"), payPrice);
    }

    private GroupBuyDiscount discount(Integer discountType, String plan, String expr, String tagId) {
        GroupBuyDiscount discount = new GroupBuyDiscount();
        discount.setDiscountId("D10001");
        discount.setDiscountType(discountType);
        discount.setMarketPlan(plan);
        discount.setMarketExpr(expr);
        discount.setTagId(tagId);
        return discount;
    }

    private static class FakeGroupBuyMarketRepository implements GroupBuyMarketRepository {

        private final boolean inCrowdRange;

        private FakeGroupBuyMarketRepository(boolean inCrowdRange) {
            this.inCrowdRange = inCrowdRange;
        }

        @Override
        public Optional<GroupBuyMarketSku> querySkuByGoodsId(String goodsId) {
            return Optional.empty();
        }

        @Override
        public Optional<SourceChannelSkuActivity> querySourceChannelSkuActivity(String source, String channel, String goodsId) {
            return Optional.empty();
        }

        @Override
        public Optional<GroupBuyDiscount> queryDiscountByDiscountId(String discountId) {
            return Optional.empty();
        }

        @Override
        public boolean isTagCrowdRange(String tagId, String userId) {
            return inCrowdRange;
        }
    }
}















