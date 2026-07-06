package com.linrun.infrastructure.quota.repository;

import com.linrun.domain.quota.adapter.QuotaProductRepository;
import com.linrun.domain.quota.model.QuotaProduct;
import com.linrun.domain.quota.service.QuotaProductKeywordService;
import com.linrun.infrastructure.dao.IQuotaProductDao;
import com.linrun.infrastructure.quota.converter.QuotaPOConverter;
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
        return QuotaPOConverter.toQuotaProducts(
                        guideDataDao.queryCandidateProducts(quotaProductKeywordService.extractKeywords(question), safeLimit))
                .stream()
                .map(this::normalizeProduct)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<QuotaProduct> queryProductByGoodsId(String goodsId) {
        QuotaProduct product = QuotaPOConverter.toEntity(guideDataDao.queryProductByGoodsId(goodsId));
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















