package com.linrun.trigger.http.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linrun.api.dto.GroupBuyActivityAdminRequest;
import com.linrun.api.dto.GroupBuyActivityStockRequest;
import com.linrun.domain.quota.adapter.QuotaProductRepository;
import com.linrun.domain.quota.model.QuotaProduct;
import com.linrun.domain.quota.service.QuotaPackageCatalogService;
import com.linrun.domain.market.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.market.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.market.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.market.adapter.repository.GroupBuyStockRepository;
import com.linrun.domain.market.model.GroupBuyActivity;
import com.linrun.domain.market.model.GroupBuyDiscount;
import com.linrun.domain.market.model.GroupBuyMarketSku;
import com.linrun.domain.market.model.GroupBuyStock;
import com.linrun.domain.market.model.SourceChannelSkuActivity;
import com.linrun.domain.market.service.GroupBuyActivityAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GroupBuyActivityAdminControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final LocalDateTime START = LocalDateTime.now().plusMinutes(1);
    private static final LocalDateTime END = LocalDateTime.now().plusDays(7);

    @Test
    void shouldListCreateToggleStockAndDeleteActivity() throws Exception {
        FakeActivityRepository activityRepo = new FakeActivityRepository();
        FakeStockRepository stockRepo = new FakeStockRepository();
        FakeMarketRepository marketRepo = new FakeMarketRepository();
        GroupBuyActivityAdminService service = new GroupBuyActivityAdminService(
                activityRepo, stockRepo, GroupBuyOrderLockRepository.noop());
        QuotaPackageCatalogService catalogService = new QuotaPackageCatalogService(new FakeQuotaProductRepository(), null);
        GroupBuyActivityAdminController controller = new GroupBuyActivityAdminController(
                service, stockRepo, marketRepo, catalogService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/v1/market/admin/goods-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data[0].goodsId").value("G10001"));

        mockMvc.perform(get("/api/v1/market/admin/discount-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].discountId").value("D10001"));

        GroupBuyActivityAdminRequest create = new GroupBuyActivityAdminRequest();
        create.setActivityName("接口测试活动");
        create.setGoodsId("G10001");
        create.setGroupPrice(new BigDecimal("16.90"));
        create.setTeamSize(3);
        create.setStartTime(START);
        create.setEndTime(END);
        create.setTotalStock(50);

        String body = MAPPER.writeValueAsString(create);
        mockMvc.perform(post("/api/v1/market/admin/activities").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.activityId").exists())
                .andExpect(jsonPath("$.data.totalStock").value(50));
        String activityId = activityRepo.firstActivityId();

        mockMvc.perform(get("/api/v1/market/admin/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].activityId").value(activityId));

        mockMvc.perform(put("/api/v1/market/admin/activities/" + activityId + "/enabled?enabled=false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        GroupBuyActivityStockRequest stockReq = new GroupBuyActivityStockRequest();
        stockReq.setTotalStock(200);
        mockMvc.perform(put("/api/v1/market/admin/activities/" + activityId + "/stock")
                        .contentType("application/json").content(MAPPER.writeValueAsString(stockReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStock").value(200));

        mockMvc.perform(delete("/api/v1/market/admin/activities/" + activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    private static class FakeActivityRepository implements GroupBuyActivityRepository {
        private final Map<String, GroupBuyActivity> store = new HashMap<>();

        @Override
        public Optional<GroupBuyActivity> queryByGoodsId(String goodsId) {
            return store.values().stream().filter(a -> a.getGoodsId().equals(goodsId)).findFirst();
        }

        @Override
        public Optional<GroupBuyActivity> queryByActivityId(String activityId) {
            return Optional.ofNullable(store.get(activityId));
        }

        @Override
        public List<GroupBuyActivity> queryActivityList(int limit) {
            return List.copyOf(store.values());
        }

        @Override
        public GroupBuyActivity save(GroupBuyActivity activity) {
            store.put(activity.getActivityId(), activity);
            return activity;
        }

        @Override
        public GroupBuyActivity update(GroupBuyActivity activity) {
            store.put(activity.getActivityId(), activity);
            return activity;
        }

        @Override
        public boolean updateEnabled(String activityId, boolean enabled) {
            GroupBuyActivity activity = store.get(activityId);
            if (activity == null) {
                return false;
            }
            activity.setEnabled(enabled);
            return true;
        }

        @Override
        public boolean removeByActivityId(String activityId) {
            return store.remove(activityId) != null;
        }

        String firstActivityId() {
            return store.keySet().iterator().next();
        }
    }

    private static class FakeStockRepository implements GroupBuyStockRepository {
        private GroupBuyStock stock;

        @Override
        public GroupBuyStock lockStock(String activityId, String goodsId, String orderId, String teamId) {
            return stock;
        }

        @Override
        public GroupBuyStock markPaidStock(String activityId, String goodsId, String orderId, String teamId) {
            return stock;
        }

        @Override
        public GroupBuyStock releaseLockedStock(String activityId, String goodsId, String orderId, String teamId) {
            return stock;
        }

        @Override
        public GroupBuyStock releasePaidStock(String activityId, String goodsId, String orderId, String teamId) {
            return stock;
        }

        @Override
        public Optional<GroupBuyStock> queryByActivityId(String activityId) {
            return Optional.ofNullable(stock);
        }

        @Override
        public GroupBuyStock initStock(String activityId, String goodsId, int totalStock) {
            stock = new GroupBuyStock();
            stock.setActivityId(activityId);
            stock.setGoodsId(goodsId);
            stock.setTotalStock(totalStock);
            stock.setAvailableStock(totalStock);
            stock.setLockedStock(0);
            stock.setPaidStock(0);
            return stock;
        }

        @Override
        public GroupBuyStock updateTotalStock(String activityId, int totalStock) {
            stock.setTotalStock(totalStock);
            stock.setAvailableStock(totalStock);
            return stock;
        }

        @Override
        public boolean removeByActivityId(String activityId) {
            stock = null;
            return true;
        }
    }

    private static class FakeMarketRepository implements GroupBuyMarketRepository {
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
            return true;
        }

        @Override
        public List<GroupBuyDiscount> queryDiscountList(int limit) {
            GroupBuyDiscount discount = new GroupBuyDiscount();
            discount.setDiscountId("D10001");
            discount.setDiscountName("直减");
            discount.setMarketPlan("ZJ");
            discount.setMarketExpr("3");
            return List.of(discount);
        }
    }
    private static class FakeQuotaProductRepository implements QuotaProductRepository {
        @Override
        public List<QuotaProduct> queryCandidateProducts(String question, int limit) {
            QuotaProduct product = new QuotaProduct();
            product.setGoodsId("G10001");
            product.setGoodsName("测试额度包");
            product.setOriginPrice(new BigDecimal("19.90"));
            product.setProductType("QUOTA_PACKAGE");
            return List.of(product);
        }

        @Override
        public Optional<QuotaProduct> queryProductByGoodsId(String goodsId) {
            return Optional.empty();
        }
    }
}
