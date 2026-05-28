package com.linrun.infrastructure.adapter.port;

import com.linrun.domain.agent.conversation.adapter.ProductRpcClient;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.knowledge.service.KnowledgeKeywordService;
import com.linrun.infrastructure.converter.AgentPOConverter;
import com.linrun.infrastructure.dao.IGuideDataDao;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
public class LocalProductRpcClient implements ProductRpcClient {

    private final IGuideDataDao guideDataDao;
    private final KnowledgeKeywordService knowledgeKeywordService;

    public LocalProductRpcClient(IGuideDataDao guideDataDao,
                                 KnowledgeKeywordService knowledgeKeywordService) {
        this.guideDataDao = guideDataDao;
        this.knowledgeKeywordService = knowledgeKeywordService;
    }

    @Override
    public List<GuideProduct> queryProducts(String keyword, int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 50);
        return AgentPOConverter.toGuideProducts(
                        guideDataDao.queryCandidateProducts(knowledgeKeywordService.extractKeywords(keyword), safeLimit))
                .stream()
                .map(this::normalize)
                .toList();
    }

    @Override
    public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
        return Optional.ofNullable(AgentPOConverter.toEntity(guideDataDao.queryProductByGoodsId(goodsId)))
                .map(this::normalize);
    }

    private GuideProduct normalize(GuideProduct product) {
        if (product.getGroupPrice() == null) {
            product.setGroupPrice(product.getOriginPrice());
        }
        if (product.getTeamSize() == null) {
            product.setTeamSize(1);
        }
        if (product.getRemainingSeconds() == null || product.getRemainingSeconds() <= 0) {
            product.setRemainingSeconds((int) Duration.ofMinutes(30).toSeconds());
        }
        return product;
    }
}
