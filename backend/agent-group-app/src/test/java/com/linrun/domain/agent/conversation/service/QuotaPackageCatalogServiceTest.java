package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.adapter.QuotaProductRepository;
import com.linrun.domain.agent.conversation.model.QuotaProduct;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.service.GroupBuyActivityService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuotaPackageCatalogServiceTest {

    @Test
    void shouldListPackagesWithGroupBuyTrialInfo() {
        QuotaPackageCatalogService service = new QuotaPackageCatalogService(
                new FakeQuotaProductRepository(),
                new GroupBuyActivityService(new FakeGroupBuyActivityRepository()));

        List<QuotaProduct> packages = service.listPackages("", 10);

        assertEquals(3, packages.size());
        assertEquals("A10001", packages.get(0).getActivityId());
        assertEquals(new BigDecimal("19.90"), packages.get(0).getGroupPrice());
        assertEquals(3, packages.get(0).getTeamSize());
        assertEquals("MEMBER_PLUS_MONTH", packages.get(2).getGoodsId());
        assertEquals("MEMBERSHIP_PLAN", packages.get(2).getProductType());
        assertEquals("A10001", packages.get(2).getActivityId());
        assertEquals(new BigDecimal("19.90"), packages.get(2).getGroupPrice());
        assertEquals(3, packages.get(2).getTeamSize());
    }

    @Test
    void shouldQueryPackageDetail() {
        QuotaPackageCatalogService service = new QuotaPackageCatalogService(
                new FakeQuotaProductRepository(),
                new GroupBuyActivityService(new FakeGroupBuyActivityRepository()));

        QuotaProduct product = service.queryPackageDetail("G10002");

        assertEquals("G10002", product.getGoodsId());
        assertEquals("长文档额度包", product.getGoodsName());
    }

    @Test
    void shouldQueryMembershipPlanDetail() {
        QuotaPackageCatalogService service = new QuotaPackageCatalogService(
                new FakeQuotaProductRepository(),
                new GroupBuyActivityService(new FakeGroupBuyActivityRepository()));

        QuotaProduct product = service.queryPackageDetail("MEMBER_PLUS_MONTH");

        assertEquals("MEMBER_PLUS_MONTH", product.getGoodsId());
        assertEquals("Plus 会员", product.getGoodsName());
        assertEquals("MEMBERSHIP_PLAN", product.getProductType());
        assertEquals("A10001", product.getActivityId());
        assertEquals(3, product.getTeamSize());
    }

    private static class FakeQuotaProductRepository implements QuotaProductRepository {

        @Override
        public List<QuotaProduct> queryCandidateProducts(String question, int limit) {
            return List.of(
                    product("G10001", "基础额度包"),
                    product("G10002", "长文档额度包"),
                    membershipPlan("MEMBER_PLUS_MONTH", "Plus 会员"));
        }

        @Override
        public Optional<QuotaProduct> queryProductByGoodsId(String goodsId) {
            return queryCandidateProducts(goodsId, 10).stream()
                    .filter(product -> goodsId.equals(product.getGoodsId()))
                    .findFirst();
        }

        private QuotaProduct product(String goodsId, String goodsName) {
            QuotaProduct product = new QuotaProduct();
            product.setGoodsId(goodsId);
            product.setGoodsName(goodsName);
            product.setOriginPrice(new BigDecimal("29.90"));
            product.setGroupPrice(new BigDecimal("24.90"));
            product.setQuotaAmount(new BigDecimal("4990"));
            product.setProductType("QUOTA_PACKAGE");
            product.setSpecSummary("适合文件问答、长文档摘要和资料整理");
            product.setRecommendReason("适合轻量 Agent 任务");
            product.setAfterSalePolicy("未使用额度可按规则退款");
            return product;
        }

        private QuotaProduct membershipPlan(String goodsId, String goodsName) {
            QuotaProduct product = new QuotaProduct();
            product.setGoodsId(goodsId);
            product.setGoodsName(goodsName);
            product.setOriginPrice(new BigDecimal("39.90"));
            product.setQuotaAmount(new BigDecimal("1000"));
            product.setProductType("MEMBERSHIP_PLAN");
            product.setSpecSummary("每月会员额度和自定义模型权益");
            product.setRecommendReason("适合高频使用学术助手");
            product.setAfterSalePolicy("会员开通后按虚拟服务规则处理售后");
            return product;
        }
    }

    private static class FakeGroupBuyActivityRepository implements GroupBuyActivityRepository {

        @Override
        public Optional<GroupBuyActivity> queryByGoodsId(String goodsId) {
            GroupBuyActivity activity = new GroupBuyActivity();
            activity.setActivityId("A10001");
            activity.setGoodsId(goodsId);
            activity.setGroupPrice(new BigDecimal("19.90"));
            activity.setTeamSize(3);
            activity.setStartTime(LocalDateTime.now().minusMinutes(10));
            activity.setEndTime(LocalDateTime.now().plusMinutes(30));
            activity.setEnabled(true);
            return Optional.of(activity);
        }

        @Override
        public Optional<GroupBuyActivity> queryByActivityId(String activityId) {
            return Optional.empty();
        }
    }
}















