package com.linrun.domain.marketing.service;

import com.linrun.domain.dcc.adapter.DynamicConfigRepository;
import com.linrun.domain.dcc.model.DynamicConfig;
import com.linrun.domain.dcc.service.DynamicConfigService;
import com.linrun.domain.marketing.adapter.GroupBuyActivityRepository;
import com.linrun.domain.marketing.adapter.GroupBuyMarketRepository;
import com.linrun.domain.marketing.model.GroupBuyActivity;
import com.linrun.domain.marketing.model.GroupBuyDiscount;
import com.linrun.domain.marketing.model.GroupBuyMarketSku;
import com.linrun.domain.marketing.model.GroupBuyMarketTrialCommand;
import com.linrun.domain.marketing.model.GroupBuyTrialResult;
import com.linrun.domain.marketing.model.SourceChannelSkuActivity;
import com.linrun.domain.marketing.service.discount.DiscountCalculateService;
import com.linrun.domain.marketing.service.discount.MJCalculateService;
import com.linrun.domain.marketing.service.discount.NCalculateService;
import com.linrun.domain.marketing.service.discount.ZJCalculateService;
import com.linrun.domain.marketing.service.discount.ZKCalculateService;
import com.linrun.domain.conversation.adapter.GuideDataRepository;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.conversation.model.GuideReference;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupBuyMarketTrialServiceTest {

    @Test
    void shouldTrialBySourceChannelSkuActivityAndDiscountPlan() {
        FakeGroupBuyActivityRepository activityRepository = new FakeGroupBuyActivityRepository();
        FakeGroupBuyMarketRepository marketRepository = new FakeGroupBuyMarketRepository();
        GroupBuyMarketTrialService service = service(activityRepository, marketRepository);

        GroupBuyTrialResult result = service.trial(command("U10001", "G10001", "s01", "c01"));

        assertEquals("A10001", result.getActivityId());
        assertEquals("Demo Goods", result.getGoodsName());
        assertEquals(new BigDecimal("2399.00"), result.getOriginalPrice());
        assertEquals(new BigDecimal("300.00"), result.getDeductionPrice());
        assertEquals(new BigDecimal("2099.00"), result.getPayPrice());
        assertEquals(3, result.getTeamSize());
        assertTrue(result.isVisible());
        assertTrue(result.isEnable());
        assertTrue(result.isAvailable());
    }

    @Test
    void shouldApplyTagParticipationScope() {
        FakeGroupBuyActivityRepository activityRepository = new FakeGroupBuyActivityRepository();
        activityRepository.activities.get("A10001").setTagId("TAG_SCOPE");
        activityRepository.activities.get("A10001").setTagScope("2");
        FakeGroupBuyMarketRepository marketRepository = new FakeGroupBuyMarketRepository();
        marketRepository.inCrowdRange = false;
        GroupBuyMarketTrialService service = service(activityRepository, marketRepository);

        GroupBuyTrialResult result = service.trial(command("U10001", "G10001", "s01", "c01"));

        assertTrue(result.isVisible());
        assertFalse(result.isEnable());
        assertFalse(result.isAvailable());
    }

    private GroupBuyMarketTrialService service(FakeGroupBuyActivityRepository activityRepository,
                                               FakeGroupBuyMarketRepository marketRepository) {
        Map<String, DiscountCalculateService> discountServices = new HashMap<>();
        discountServices.put("ZJ", new ZJCalculateService(marketRepository));
        discountServices.put("MJ", new MJCalculateService(marketRepository));
        discountServices.put("ZK", new ZKCalculateService(marketRepository));
        discountServices.put("N", new NCalculateService(marketRepository));
        return new GroupBuyMarketTrialService(
                activityRepository,
                marketRepository,
                new FakeGuideDataRepository(),
                new DynamicConfigService(new FakeDynamicConfigRepository()),
                discountServices);
    }

    private GroupBuyMarketTrialCommand command(String userId, String goodsId, String source, String channel) {
        GroupBuyMarketTrialCommand command = new GroupBuyMarketTrialCommand();
        command.setUserId(userId);
        command.setGoodsId(goodsId);
        command.setSource(source);
        command.setChannel(channel);
        return command;
    }

    private static GroupBuyActivity activity() {
        GroupBuyActivity activity = new GroupBuyActivity();
        activity.setActivityId("A10001");
        activity.setActivityName("Demo Activity");
        activity.setGoodsId("G10001");
        activity.setGroupPrice(new BigDecimal("2099.00"));
        activity.setTeamSize(3);
        activity.setTarget(3);
        activity.setTakeLimitCount(2);
        activity.setValidTime(1440);
        activity.setStatus(1);
        activity.setDiscountId("D10001");
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusDays(1));
        activity.setEnabled(true);
        return activity;
    }

    private static class FakeGroupBuyActivityRepository implements GroupBuyActivityRepository {

        private final Map<String, GroupBuyActivity> activities = new HashMap<>();

        private FakeGroupBuyActivityRepository() {
            activities.put("A10001", activity());
        }

        @Override
        public Optional<GroupBuyActivity> queryByGoodsId(String goodsId) {
            return activities.values().stream()
                    .filter(activity -> goodsId.equals(activity.getGoodsId()))
                    .findFirst();
        }

        @Override
        public Optional<GroupBuyActivity> queryByActivityId(String activityId) {
            return Optional.ofNullable(activities.get(activityId));
        }
    }

    private static class FakeGroupBuyMarketRepository implements GroupBuyMarketRepository {

        private boolean inCrowdRange = true;

        @Override
        public Optional<GroupBuyMarketSku> querySkuByGoodsId(String goodsId) {
            GroupBuyMarketSku sku = new GroupBuyMarketSku();
            sku.setGoodsId(goodsId);
            sku.setGoodsName("Demo Goods");
            sku.setOriginalPrice(new BigDecimal("2399.00"));
            return Optional.of(sku);
        }

        @Override
        public Optional<SourceChannelSkuActivity> querySourceChannelSkuActivity(String source, String channel, String goodsId) {
            SourceChannelSkuActivity relation = new SourceChannelSkuActivity();
            relation.setSource(source);
            relation.setChannel(channel);
            relation.setGoodsId(goodsId);
            relation.setActivityId("A10001");
            return Optional.of(relation);
        }

        @Override
        public Optional<GroupBuyDiscount> queryDiscountByDiscountId(String discountId) {
            GroupBuyDiscount discount = new GroupBuyDiscount();
            discount.setDiscountId(discountId);
            discount.setDiscountName("Direct Reduction");
            discount.setDiscountType(0);
            discount.setMarketPlan("ZJ");
            discount.setMarketExpr("300");
            return Optional.of(discount);
        }

        @Override
        public boolean isTagCrowdRange(String tagId, String userId) {
            return inCrowdRange;
        }
    }

    private static class FakeGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            return List.of();
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            return Optional.empty();
        }

        @Override
        public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
            return Optional.empty();
        }
    }

    private static class FakeDynamicConfigRepository implements DynamicConfigRepository {

        @Override
        public Optional<DynamicConfig> queryByKey(String configKey) {
            return Optional.empty();
        }

        @Override
        public void saveOrUpdate(DynamicConfig config) {
        }
    }
}
