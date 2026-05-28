package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.activity.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.activity.model.GroupBuyActivity;
import com.linrun.domain.activity.service.GroupBuyActivityService;
import com.linrun.domain.agent.conversation.adapter.ProductRpcClient;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductCatalogServiceTest {

    @Test
    void shouldListProductsWithGroupBuyTrialInfo() {
        ProductCatalogService service = new ProductCatalogService(
                new FakeProductRpcClient(),
                new GroupBuyActivityService(new FakeGroupBuyActivityRepository()));

        List<GuideProduct> products = service.listProducts("", 10);

        assertEquals(2, products.size());
        assertEquals("A10001", products.get(0).getActivityId());
        assertEquals(new BigDecimal("2099.00"), products.get(0).getGroupPrice());
        assertEquals(3, products.get(0).getTeamSize());
    }

    @Test
    void shouldQueryProductDetail() {
        ProductCatalogService service = new ProductCatalogService(
                new FakeProductRpcClient(),
                new GroupBuyActivityService(new FakeGroupBuyActivityRepository()));

        GuideProduct product = service.queryProductDetail("G10002");

        assertEquals("G10002", product.getGoodsId());
        assertEquals("高配创作平板", product.getGoodsName());
    }

    private static class FakeProductRpcClient implements ProductRpcClient {

        @Override
        public List<GuideProduct> queryProducts(String question, int limit) {
            return List.of(product("G10001", "轻薄学习平板标准版"), product("G10002", "高配创作平板"));
        }

        @Override
        public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
            return queryProducts(goodsId, 10).stream()
                    .filter(product -> goodsId.equals(product.getGoodsId()))
                    .findFirst();
        }

        private GuideProduct product(String goodsId, String goodsName) {
            GuideProduct product = new GuideProduct();
            product.setGoodsId(goodsId);
            product.setGoodsName(goodsName);
            product.setOriginPrice(new BigDecimal("2399.00"));
            product.setGroupPrice(new BigDecimal("2199.00"));
            product.setSpecSummary("学习、办公和创作平板");
            product.setRecommendReason("适合日常学习和办公");
            product.setAfterSalePolicy("7 天无理由退货，1 年质保");
            return product;
        }
    }

    private static class FakeGroupBuyActivityRepository implements GroupBuyActivityRepository {

        @Override
        public Optional<GroupBuyActivity> queryByGoodsId(String goodsId) {
            GroupBuyActivity activity = new GroupBuyActivity();
            activity.setActivityId("A10001");
            activity.setGoodsId(goodsId);
            activity.setGroupPrice(new BigDecimal("2099.00"));
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
