package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.knowledge.adapter.KnowledgeVectorRepository;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.service.KnowledgeKeywordService;
import com.linrun.domain.agent.knowledge.service.KnowledgeVectorService;
import com.linrun.infrastructure.dao.IGuideDataDao;
import com.linrun.infrastructure.po.GuideProductPO;
import com.linrun.infrastructure.po.GuideReferencePO;
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

        repository.queryReferences("outbox retry", 3);

        assertEquals(6, guideDataDao.limit);
        assertTrue(guideDataDao.keywords.contains("outbox"));
        assertTrue(guideDataDao.keywords.contains("retry"));
    }

    @Test
    void shouldFuseVectorAndKeywordReferences() {
        FakeGuideDataDao guideDataDao = new FakeGuideDataDao();
        guideDataDao.references = List.of(reference("KF90001", "product detail", "standard tablet for study"));
        FakeKnowledgeVectorRepository vectorRepository = new FakeKnowledgeVectorRepository(List.of(fragment("KF90001")));
        MyBatisGuideDataRepository repository = new MyBatisGuideDataRepository(
                guideDataDao,
                new KnowledgeKeywordService(),
                new KnowledgeVectorService(vectorRepository));

        List<GuideReference> references = repository.queryReferences("student tablet", 3);

        assertEquals(1, references.size());
        assertEquals("KF90001", references.get(0).getFragmentId());
        assertEquals(1, guideDataDao.queryCount);
    }

    @Test
    void shouldRerankKeywordHitWhenBusinessTermMatchesBetter() {
        FakeGuideDataDao guideDataDao = new FakeGuideDataDao();
        guideDataDao.references = List.of(reference("KF90002", "refund policy", "group refund supported"));
        FakeKnowledgeVectorRepository vectorRepository = new FakeKnowledgeVectorRepository(
                List.of(fragment("KF90001", "standard tablet for study")));
        MyBatisGuideDataRepository repository = new MyBatisGuideDataRepository(
                guideDataDao,
                new KnowledgeKeywordService(),
                new KnowledgeVectorService(vectorRepository));

        List<GuideReference> references = repository.queryReferences("group refund", 3);

        assertEquals("KF90002", references.get(0).getFragmentId());
        assertEquals(1, references.get(0).getRank());
    }

    @Test
    void shouldExpandVectorHitToParentAndSiblingReferences() {
        FakeGuideDataDao guideDataDao = new FakeGuideDataDao();
        GuideReferencePO parent = reference("KFPARENT", "policy", "parent full refund policy");
        GuideReferencePO sibling = reference("KFSIBLING", "policy", "sibling unformed group refund");
        sibling.setBrotherGroupId("BRO90001");
        GuideReferencePO child = reference("KFCHILD", "policy", "child refund");
        child.setParentFragmentId("KFPARENT");
        child.setBrotherGroupId("BRO90001");
        guideDataDao.references = List.of(parent, sibling, child);
        KnowledgeFragment hit = fragment("KFCHILD", "child refund");
        hit.setParentFragmentId("KFPARENT");
        hit.setBrotherGroupId("BRO90001");
        FakeKnowledgeVectorRepository vectorRepository = new FakeKnowledgeVectorRepository(List.of(hit));
        MyBatisGuideDataRepository repository = new MyBatisGuideDataRepository(
                guideDataDao,
                new KnowledgeKeywordService(),
                new KnowledgeVectorService(vectorRepository));

        List<GuideReference> references = repository.queryReferences("refund", 5);

        assertTrue(references.stream().anyMatch(reference -> "KFPARENT".equals(reference.getFragmentId())));
        assertTrue(references.stream().anyMatch(reference -> "KFSIBLING".equals(reference.getFragmentId())));
        assertTrue(references.stream().anyMatch(reference -> "KFCHILD".equals(reference.getFragmentId())));
    }

    @Test
    void shouldQueryCandidateProductsByExtractedKeywords() {
        FakeGuideDataDao guideDataDao = new FakeGuideDataDao();
        MyBatisGuideDataRepository repository = new MyBatisGuideDataRepository(
                guideDataDao,
                new KnowledgeKeywordService());

        List<GuideProduct> products = repository.queryCandidateProducts("video editing high performance", 5);

        assertEquals(1, products.size());
        assertEquals("G10002", products.get(0).getGoodsId());
        assertTrue(guideDataDao.keywords.contains("video"));
        assertEquals(5, guideDataDao.limit);
    }

    private static class FakeGuideDataDao implements IGuideDataDao {

        private List<String> keywords;
        private int limit;
        private int queryCount;
        private List<GuideReferencePO> references = List.of();

        @Override
        public List<GuideReferencePO> queryReferences(List<String> keywords, int limit) {
            this.keywords = keywords;
            this.limit = limit;
            this.queryCount++;
            return references;
        }

        @Override
        public GuideReferencePO queryReferenceByFragmentId(String fragmentId) {
            return references.stream()
                    .filter(reference -> fragmentId.equals(reference.getFragmentId()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<GuideReferencePO> querySiblingReferences(String brotherGroupId, int limit) {
            return references.stream()
                    .filter(reference -> brotherGroupId.equals(reference.getBrotherGroupId()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<GuideProductPO> queryCandidateProducts(List<String> keywords, int limit) {
            this.keywords = keywords;
            this.limit = limit;
            GuideProductPO product = new GuideProductPO();
            product.setGoodsId("G10002");
            product.setGoodsName("creator tablet");
            return List.of(product);
        }

        @Override
        public GuideProductPO queryRecommendProduct(String question) {
            return null;
        }

        @Override
        public GuideProductPO queryProductByGoodsId(String goodsId) {
            return null;
        }
    }

    private KnowledgeFragment fragment(String fragmentId) {
        return fragment(fragmentId, "standard tablet for study");
    }

    private KnowledgeFragment fragment(String fragmentId, String content) {
        KnowledgeFragment fragment = new KnowledgeFragment();
        fragment.setFragmentId(fragmentId);
        fragment.setDocumentId("DOC90001");
        fragment.setGoodsId("G10001");
        fragment.setDocumentType("product detail");
        fragment.setKnowledgeVersion("v1");
        fragment.setContent(content);
        return fragment;
    }

    private GuideReferencePO reference(String fragmentId, String documentType, String content) {
        GuideReferencePO reference = new GuideReferencePO();
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
