package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.conversation.adapter.GuideDataRepository;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.knowledge.service.KnowledgeKeywordService;
import com.linrun.infrastructure.agent.converter.AgentPOConverter;
import com.linrun.infrastructure.dao.IGuideDataDao;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisGuideDataRepository implements GuideDataRepository {

    private final IGuideDataDao guideDataDao;
    private final KnowledgeKeywordService knowledgeKeywordService;

    public MyBatisGuideDataRepository(IGuideDataDao guideDataDao,
                                      KnowledgeKeywordService knowledgeKeywordService) {
        this.guideDataDao = guideDataDao;
        this.knowledgeKeywordService = knowledgeKeywordService;
    }

    @Override
    public List<GuideProduct> queryCandidateProducts(String question, int limit) {
        int safeLimit = limit <= 0 ? 5 : limit;
        return AgentPOConverter.toGuideProducts(
                        guideDataDao.queryCandidateProducts(knowledgeKeywordService.extractKeywords(question), safeLimit))
                .stream()
                .map(this::normalizeProduct)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
        GuideProduct product = AgentPOConverter.toEntity(guideDataDao.queryProductByGoodsId(goodsId));
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
}
