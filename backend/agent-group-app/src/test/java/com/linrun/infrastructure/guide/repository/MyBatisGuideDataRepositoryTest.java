package com.linrun.infrastructure.guide.repository;

import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.domain.knowledge.service.KnowledgeKeywordService;
import com.linrun.infrastructure.dao.IGuideDataDao;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MyBatisGuideDataRepositoryTest {

    @Test
    void shouldQueryReferencesByExtractedKeywords() {
        FakeGuideDataDao guideDataDao = new FakeGuideDataDao();
        MyBatisGuideDataRepository repository = new MyBatisGuideDataRepository(
                guideDataDao,
                new KnowledgeKeywordService());

        repository.queryReferences("我想了解拼团退款规则", 3);

        assertEquals(3, guideDataDao.limit);
        assertTrue(guideDataDao.keywords.contains("拼团"));
        assertTrue(guideDataDao.keywords.contains("退款"));
    }

    private static class FakeGuideDataDao implements IGuideDataDao {

        private List<String> keywords;
        private int limit;

        @Override
        public List<GuideReference> queryReferences(List<String> keywords, int limit) {
            this.keywords = keywords;
            this.limit = limit;
            return List.of();
        }

        @Override
        public GuideProduct queryRecommendProduct(String question) {
            return null;
        }

        @Override
        public GuideProduct queryProductByGoodsId(String goodsId) {
            return null;
        }
    }
}
