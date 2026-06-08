package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.knowledge.service.KnowledgeKeywordService;
import com.linrun.infrastructure.dao.IGuideDataDao;
import com.linrun.infrastructure.po.GuideProductPO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyBatisGuideDataRepositoryTest {

    @Test
    void shouldQueryCandidateProductsByExtractedKeywords() {
        FakeGuideDataDao guideDataDao = new FakeGuideDataDao();
        MyBatisGuideDataRepository repository = new MyBatisGuideDataRepository(
                guideDataDao,
                new KnowledgeKeywordService());

        List<GuideProduct> products = repository.queryCandidateProducts("paper reading", 5);

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
        MyBatisGuideDataRepository repository = new MyBatisGuideDataRepository(
                guideDataDao,
                new KnowledgeKeywordService());

        GuideProduct product = repository.queryProductByGoodsId("G10001").orElseThrow();

        assertEquals(new BigDecimal("29.90"), product.getGroupPrice());
        assertEquals(1, product.getTeamSize());
        assertEquals(1800, product.getRemainingSeconds());
    }

    private static class FakeGuideDataDao implements IGuideDataDao {

        private List<String> keywords = List.of();
        private int limit;
        private GuideProductPO detail;

        @Override
        public List<GuideProductPO> queryCandidateProducts(List<String> keywords, int limit) {
            this.keywords = keywords;
            this.limit = limit;
            return List.of(product("G10002", "论文阅读额度包"));
        }

        @Override
        public GuideProductPO queryProductByGoodsId(String goodsId) {
            return detail;
        }

        private static GuideProductPO product(String goodsId, String goodsName) {
            GuideProductPO product = new GuideProductPO();
            product.setGoodsId(goodsId);
            product.setGoodsName(goodsName);
            product.setOriginPrice(new BigDecimal("29.90"));
            return product;
        }
    }
}
