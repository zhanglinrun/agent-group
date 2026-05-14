package com.linrun.infrastructure.guide.repository;

import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.domain.knowledge.model.KnowledgeFragment;
import com.linrun.domain.knowledge.service.KnowledgeKeywordService;
import com.linrun.domain.knowledge.service.KnowledgeVectorService;
import com.linrun.infrastructure.dao.IGuideDataDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Repository
public class MyBatisGuideDataRepository implements GuideDataRepository {

    private final IGuideDataDao guideDataDao;
    private final KnowledgeKeywordService knowledgeKeywordService;
    private final KnowledgeVectorService knowledgeVectorService;
    private final boolean keywordFallbackEnabled;

    @Autowired
    public MyBatisGuideDataRepository(IGuideDataDao guideDataDao,
                                      KnowledgeKeywordService knowledgeKeywordService,
                                      KnowledgeVectorService knowledgeVectorService,
                                      @Value("${agent.group.vector.keyword-fallback-enabled:true}") boolean keywordFallbackEnabled) {
        this.guideDataDao = guideDataDao;
        this.knowledgeKeywordService = knowledgeKeywordService;
        this.knowledgeVectorService = knowledgeVectorService;
        this.keywordFallbackEnabled = keywordFallbackEnabled;
    }

    public MyBatisGuideDataRepository(IGuideDataDao guideDataDao,
                                      KnowledgeKeywordService knowledgeKeywordService,
                                      KnowledgeVectorService knowledgeVectorService) {
        this(guideDataDao, knowledgeKeywordService, knowledgeVectorService, true);
    }

    public MyBatisGuideDataRepository(IGuideDataDao guideDataDao, KnowledgeKeywordService knowledgeKeywordService) {
        this(guideDataDao, knowledgeKeywordService, null);
    }

    @Override
    public List<GuideReference> queryReferences(String question, int limit) {
        List<GuideReference> vectorReferences = queryVectorReferences(question, limit);
        if (!vectorReferences.isEmpty()) {
            return vectorReferences;
        }
        if (!keywordFallbackEnabled) {
            return List.of();
        }
        return guideDataDao.queryReferences(knowledgeKeywordService.extractKeywords(question), limit);
    }

    @Override
    public List<GuideProduct> queryCandidateProducts(String question, int limit) {
        int safeLimit = limit <= 0 ? 5 : limit;
        return guideDataDao.queryCandidateProducts(knowledgeKeywordService.extractKeywords(question), safeLimit)
                .stream()
                .map(this::normalizeProduct)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<GuideProduct> queryRecommendProduct(String question) {
        GuideProduct product = guideDataDao.queryRecommendProduct(question);
        return normalizeProduct(product);
    }

    @Override
    public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
        GuideProduct product = guideDataDao.queryProductByGoodsId(goodsId);
        return normalizeProduct(product);
    }

    private Optional<GuideProduct> normalizeProduct(GuideProduct product) {
        if (product == null) {
            return Optional.empty();
        }
        if (product.getGroupPrice() == null) {
            product.setGroupPrice(product.getOriginPrice());
        }
        if (product.getTeamSize() == null) {
            product.setTeamSize(1);
        }
        if (product.getRemainingSeconds() == null || product.getRemainingSeconds() <= 0) {
            product.setRemainingSeconds((int) Duration.ofMinutes(30).toSeconds());
        }
        return Optional.of(product);
    }

    private List<GuideReference> queryVectorReferences(String question, int limit) {
        if (knowledgeVectorService == null) {
            return List.of();
        }
        try {
            AtomicInteger rank = new AtomicInteger(1);
            return knowledgeVectorService.searchSimilar(question, limit).stream()
                    .map(fragment -> toGuideReference(fragment, rank.getAndIncrement()))
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private GuideReference toGuideReference(KnowledgeFragment fragment, int rank) {
        GuideReference reference = new GuideReference();
        reference.setFragmentId(fragment.getFragmentId());
        reference.setDocumentId(fragment.getDocumentId());
        reference.setGoodsId(fragment.getGoodsId());
        reference.setDocumentType(fragment.getDocumentType());
        reference.setKnowledgeVersion(fragment.getKnowledgeVersion());
        reference.setContent(fragment.getContent());
        reference.setRank(rank);
        return reference;
    }
}
