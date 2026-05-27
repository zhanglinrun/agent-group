package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeVectorRepository;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.service.KnowledgeKeywordService;
import com.linrun.domain.agent.knowledge.service.KnowledgeVectorService;
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

        assertEquals(6, guideDataDao.limit);
        assertTrue(guideDataDao.keywords.contains("拼团"));
        assertTrue(guideDataDao.keywords.contains("退款"));
    }

    @Test
    void shouldFuseVectorAndKeywordReferences() {
        FakeGuideDataDao guideDataDao = new FakeGuideDataDao();
        FakeKnowledgeVectorRepository vectorRepository = new FakeKnowledgeVectorRepository(List.of(fragment("KF90001")));
        MyBatisGuideDataRepository repository = new MyBatisGuideDataRepository(
                guideDataDao,
                new KnowledgeKeywordService(),
                new KnowledgeVectorService(vectorRepository));

        List<GuideReference> references = repository.queryReferences("学生买平板", 3);

        assertEquals(1, references.size());
        assertEquals("KF90001", references.get(0).getFragmentId());
        assertEquals(1, guideDataDao.queryCount);
    }

    @Test
    void shouldRerankKeywordHitWhenBusinessTermMatchesBetter() {
        FakeGuideDataDao guideDataDao = new FakeGuideDataDao();
        guideDataDao.references = List.of(reference("KF90002", "售后政策", "拼团失败后会自动退款，退款会原路返回。"));
        FakeKnowledgeVectorRepository vectorRepository = new FakeKnowledgeVectorRepository(
                List.of(fragment("KF90001", "标准版适合学生写论文和看网课")));
        MyBatisGuideDataRepository repository = new MyBatisGuideDataRepository(
                guideDataDao,
                new KnowledgeKeywordService(),
                new KnowledgeVectorService(vectorRepository));

        List<GuideReference> references = repository.queryReferences("拼团失败能退款吗", 3);

        assertEquals("KF90002", references.get(0).getFragmentId());
        assertEquals(1, references.get(0).getRank());
    }

    @Test
    void shouldQueryCandidateProductsByExtractedKeywords() {
        FakeGuideDataDao guideDataDao = new FakeGuideDataDao();
        MyBatisGuideDataRepository repository = new MyBatisGuideDataRepository(
                guideDataDao,
                new KnowledgeKeywordService());

        List<GuideProduct> products = repository.queryCandidateProducts("我想剪视频，需要高配性能", 5);

        assertEquals(1, products.size());
        assertEquals("G10002", products.get(0).getGoodsId());
        assertTrue(guideDataDao.keywords.contains("剪视频"));
        assertTrue(guideDataDao.keywords.contains("高配"));
        assertEquals(5, guideDataDao.limit);
    }

    private static class FakeGuideDataDao implements IGuideDataDao {

        private List<String> keywords;
        private int limit;
        private int queryCount;
        private List<GuideReference> references = List.of();

        @Override
        public List<GuideReference> queryReferences(List<String> keywords, int limit) {
            this.keywords = keywords;
            this.limit = limit;
            this.queryCount++;
            return references;
        }

        @Override
        public List<GuideProduct> queryCandidateProducts(List<String> keywords, int limit) {
            this.keywords = keywords;
            this.limit = limit;
            GuideProduct product = new GuideProduct();
            product.setGoodsId("G10002");
            product.setGoodsName("高配创作平板");
            return List.of(product);
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
        return fragment(fragmentId, "标准版适合学生写论文和看网课");
    }

    private KnowledgeFragment fragment(String fragmentId, String content) {
        KnowledgeFragment fragment = new KnowledgeFragment();
        fragment.setFragmentId(fragmentId);
        fragment.setDocumentId("DOC90001");
        fragment.setGoodsId("G10001");
        fragment.setDocumentType("商品详情");
        fragment.setKnowledgeVersion("v1");
        fragment.setContent(content);
        return fragment;
    }

    private GuideReference reference(String fragmentId, String documentType, String content) {
        GuideReference reference = new GuideReference();
        reference.setFragmentId(fragmentId);
        reference.setDocumentId("DOC90002");
        reference.setGoodsId("G10001");
        reference.setDocumentType(documentType);
        reference.setKnowledgeVersion("v1");
        reference.setContent(content);
        return reference;
    }

    private static class FakeKnowledgeVectorRepository implements KnowledgeVectorRepository {

        private final List<KnowledgeFragment> fragments = new ArrayList<>();

        private FakeKnowledgeVectorRepository(List<KnowledgeFragment> fragments) {
            this.fragments.addAll(fragments);
        }

        @Override
        public void saveFragment(KnowledgeFragment fragment) {
            fragments.add(fragment);
        }

        @Override
        public List<KnowledgeFragment> searchSimilar(String question, int limit) {
            return fragments.stream()
                    .limit(limit)
                    .toList();
        }
    }
}
