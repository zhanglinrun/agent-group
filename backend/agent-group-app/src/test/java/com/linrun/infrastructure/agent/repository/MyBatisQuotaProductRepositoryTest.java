package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.conversation.model.QuotaProduct;
import com.linrun.domain.agent.conversation.service.QuotaProductKeywordService;
import com.linrun.infrastructure.dao.IQuotaProductDao;
import com.linrun.infrastructure.po.QuotaProductPO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyBatisQuotaProductRepositoryTest {

    @Test
    void shouldQueryCandidateProductsByExtractedKeywords() {
        FakeGuideDataDao guideDataDao = new FakeGuideDataDao();
        MyBatisQuotaProductRepository repository = new MyBatisQuotaProductRepository(
                guideDataDao,
                new QuotaProductKeywordService());

        List<QuotaProduct> products = repository.queryCandidateProducts("paper reading", 5);

        assertEquals(1, products.size());
        assertEquals("G10002", products.getFirst().getGoodsId());
        assertTrue(guideDataDao.keywords.contains("paper"));
        assertEquals(5, guideDataDao.limit);
    }

    @Test
    void shouldNormalizeProductDefaults() {
        FakeGuideDataDao guideDataDao = new FakeGuideDataDao();
        guideDataDao.detail = FakeGuideDataDao.product("G10001", "基础额度包");
        guideDataDao.detail.setGroupPrice(null);
        MyBatisQuotaProductRepository repository = new MyBatisQuotaProductRepository(
                guideDataDao,
                new QuotaProductKeywordService());

        QuotaProduct product = repository.queryProductByGoodsId("G10001").orElseThrow();

        assertEquals(new BigDecimal("29.90"), product.getGroupPrice());
        assertEquals(1, product.getTeamSize());
        assertEquals(1800, product.getRemainingSeconds());
    }

    private static class FakeGuideDataDao implements IQuotaProductDao {

        private List<String> keywords = List.of();
        private int limit;
        private QuotaProductPO detail;

        @Override
        public List<QuotaProductPO> queryCandidateProducts(List<String> keywords, int limit) {
            this.keywords = keywords;
            this.limit = limit;
            return List.of(product("G10002", "长文档额度包"));
        }

        @Override
        public QuotaProductPO queryProductByGoodsId(String goodsId) {
            return detail;
        }

        private static QuotaProductPO product(String goodsId, String goodsName) {
            QuotaProductPO product = new QuotaProductPO();
            product.setGoodsId(goodsId);
            product.setGoodsName(goodsName);
            product.setOriginPrice(new BigDecimal("29.90"));
            return product;
        }
    }
}















