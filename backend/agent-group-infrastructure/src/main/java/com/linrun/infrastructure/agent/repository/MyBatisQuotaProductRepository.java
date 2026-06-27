package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.conversation.adapter.QuotaProductRepository;
import com.linrun.domain.agent.conversation.model.QuotaProduct;
import com.linrun.domain.agent.conversation.service.QuotaProductKeywordService;
import com.linrun.infrastructure.agent.converter.AgentPOConverter;
import com.linrun.infrastructure.dao.IQuotaProductDao;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisQuotaProductRepository implements QuotaProductRepository {

    private final IQuotaProductDao guideDataDao;
    private final QuotaProductKeywordService quotaProductKeywordService;

    public MyBatisQuotaProductRepository(IQuotaProductDao guideDataDao,
                                      QuotaProductKeywordService quotaProductKeywordService) {
        this.guideDataDao = guideDataDao;
        this.quotaProductKeywordService = quotaProductKeywordService;
    }

    @Override
    public List<QuotaProduct> queryCandidateProducts(String question, int limit) {
        int safeLimit = limit <= 0 ? 5 : limit;
        return AgentPOConverter.toQuotaProducts(
                        guideDataDao.queryCandidateProducts(quotaProductKeywordService.extractKeywords(question), safeLimit))
                .stream()
                .map(this::normalizeProduct)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<QuotaProduct> queryProductByGoodsId(String goodsId) {
        QuotaProduct product = AgentPOConverter.toEntity(guideDataDao.queryProductByGoodsId(goodsId));
        return normalizeProduct(product);
    }

    private Optional<QuotaProduct> normalizeProduct(QuotaProduct product) {
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















