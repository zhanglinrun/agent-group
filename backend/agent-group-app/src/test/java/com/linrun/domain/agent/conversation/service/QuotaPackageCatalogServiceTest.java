package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.activity.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.activity.model.GroupBuyActivity;
import com.linrun.domain.activity.service.GroupBuyActivityService;
import com.linrun.domain.agent.conversation.adapter.GuideDataRepository;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideReference;
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
                new FakeGuideDataRepository(),
                new GroupBuyActivityService(new FakeGroupBuyActivityRepository()));

        List<GuideProduct> packages = service.listPackages("", 10);

        assertEquals(2, packages.size());
        assertEquals("A10001", packages.get(0).getActivityId());
        assertEquals(new BigDecimal("19.90"), packages.get(0).getGroupPrice());
        assertEquals(3, packages.get(0).getTeamSize());
    }

    @Test
    void shouldQueryPackageDetail() {
        QuotaPackageCatalogService service = new QuotaPackageCatalogService(
                new FakeGuideDataRepository(),
                new GroupBuyActivityService(new FakeGroupBuyActivityRepository()));

        GuideProduct product = service.queryPackageDetail("G10002");

        assertEquals("G10002", product.getGoodsId());
        assertEquals("论文阅读额度包", product.getGoodsName());
    }

    private static class FakeGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            return List.of();
        }

        @Override
        public List<GuideProduct> queryCandidateProducts(String question, int limit) {
            return List.of(product("G10001", "基础额度包"), product("G10002", "论文阅读额度包"));
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            return Optional.of(product("G10001", "基础额度包"));
        }

        @Override
        public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
            return queryCandidateProducts(goodsId, 10).stream()
                    .filter(product -> goodsId.equals(product.getGoodsId()))
                    .findFirst();
        }

        private GuideProduct product(String goodsId, String goodsName) {
            GuideProduct product = new GuideProduct();
            product.setGoodsId(goodsId);
            product.setGoodsName(goodsName);
            product.setOriginPrice(new BigDecimal("29.90"));
            product.setGroupPrice(new BigDecimal("24.90"));
            product.setQuotaAmount(new BigDecimal("40"));
            product.setProductType("QUOTA_PACKAGE");
            product.setSpecSummary("适合学术问答、论文摘要和资料整理");
            product.setRecommendReason("适合轻量学术任务");
            product.setAfterSalePolicy("未使用额度可按规则退款");
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
