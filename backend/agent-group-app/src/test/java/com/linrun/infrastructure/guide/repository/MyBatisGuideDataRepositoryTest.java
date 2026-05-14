package com.linrun.infrastructure.guide.repository;

import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.domain.knowledge.adapter.KnowledgeEmbeddingClient;
import com.linrun.domain.knowledge.adapter.KnowledgeVectorRepository;
import com.linrun.domain.knowledge.model.KnowledgeFragment;
import com.linrun.domain.knowledge.service.KnowledgeKeywordService;
import com.linrun.domain.knowledge.service.KnowledgeVectorService;
import com.linrun.infrastructure.dao.IGuideDataDao;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    @Test
    void shouldPreferVectorReferencesWhenAvailable() {
        FakeGuideDataDao guideDataDao = new FakeGuideDataDao();
        FakeKnowledgeVectorRepository vectorRepository = new FakeKnowledgeVectorRepository(List.of(fragment("KF90001")));
        MyBatisGuideDataRepository repository = new MyBatisGuideDataRepository(
                guideDataDao,
                new KnowledgeKeywordService(),
                new KnowledgeVectorService(new FakeKnowledgeEmbeddingClient(), vectorRepository));

        List<GuideReference> references = repository.queryReferences("学生买平板", 3);

        assertEquals(1, references.size());
        assertEquals("KF90001", references.get(0).getFragmentId());
        assertEquals(0, guideDataDao.queryCount);
    }

    private static class FakeGuideDataDao implements IGuideDataDao {

        private List<String> keywords;
        private int limit;
        private int queryCount;

        @Override
        public List<GuideReference> queryReferences(List<String> keywords, int limit) {
            this.keywords = keywords;
            this.limit = limit;
            this.queryCount++;
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

    private KnowledgeFragment fragment(String fragmentId) {
        KnowledgeFragment fragment = new KnowledgeFragment();
        fragment.setFragmentId(fragmentId);
        fragment.setDocumentId("DOC90001");
        fragment.setGoodsId("G10001");
        fragment.setDocumentType("商品详情");
        fragment.setKnowledgeVersion("v1");
        fragment.setContent("标准版适合学生写论文和看网课");
        return fragment;
    }

    private static class FakeKnowledgeEmbeddingClient implements KnowledgeEmbeddingClient {

        @Override
        public List<Double> embed(String content) {
            return List.of(1.0d);
        }
    }

    private static class FakeKnowledgeVectorRepository implements KnowledgeVectorRepository {

        private final List<KnowledgeFragment> fragments = new ArrayList<>();

        private FakeKnowledgeVectorRepository(List<KnowledgeFragment> fragments) {
            this.fragments.addAll(fragments);
        }

        @Override
        public void saveEmbedding(KnowledgeFragment fragment, List<Double> embedding) {
            fragments.add(fragment);
        }

        @Override
        public List<KnowledgeFragment> searchSimilar(List<Double> queryEmbedding, int limit) {
            return fragments.stream()
                    .limit(limit)
                    .toList();
        }
    }
}
