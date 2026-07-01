package com.linrun.domain.quota.service;

import com.linrun.domain.market.model.GroupBuyActivityStatus;
import com.linrun.domain.market.model.GroupBuyTrialResult;
import com.linrun.domain.market.service.GroupBuyActivityService;
import com.linrun.domain.quota.adapter.QuotaProductRepository;
import com.linrun.domain.quota.model.QuotaProduct;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class QuotaPackageCatalogService {

    private static final String QUOTA_PACKAGE = "QUOTA_PACKAGE";
    private static final String MEMBERSHIP_PLAN = "MEMBERSHIP_PLAN";

    private final QuotaProductRepository quotaProductRepository;
    private final GroupBuyActivityService groupBuyActivityService;

    public QuotaPackageCatalogService(QuotaProductRepository quotaProductRepository,
                                      GroupBuyActivityService groupBuyActivityService) {
        this.quotaProductRepository = quotaProductRepository;
        this.groupBuyActivityService = groupBuyActivityService;
    }

    public List<QuotaProduct> listPackages(String keyword, int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 50);
        String query = StringUtils.hasText(keyword) ? keyword : "";
        return quotaProductRepository.queryCandidateProducts(query, safeLimit).stream()
                .filter(this::isUpgradeProduct)
                .map(this::enrichGroupBuy)
                .toList();
    }

    public QuotaProduct queryPackageDetail(String goodsId) {
        if (!StringUtils.hasText(goodsId)) {
            throw new AppException("0001", "额度包编号不能为空");
        }
        QuotaProduct product = quotaProductRepository.queryProductByGoodsId(goodsId)
                .filter(this::isUpgradeProduct)
                .orElseThrow(() -> new AppException("DATA_0003", "套餐不存在或已下架"));
        return enrichGroupBuy(product);
    }

    private boolean isUpgradeProduct(QuotaProduct product) {
        return isQuotaPackage(product) || isMembershipPlan(product);
    }

    private boolean isQuotaPackage(QuotaProduct product) {
        return product != null && QUOTA_PACKAGE.equals(product.getProductType());
    }

    private boolean isMembershipPlan(QuotaProduct product) {
        return product != null && MEMBERSHIP_PLAN.equals(product.getProductType());
    }

    private QuotaProduct enrichGroupBuy(QuotaProduct product) {
        if (product == null || !isUpgradeProduct(product) || !StringUtils.hasText(product.getGoodsId()) || groupBuyActivityService == null) {
            return product;
        }
        try {
            GroupBuyTrialResult trialResult = groupBuyActivityService.trial(product.getGoodsId());
            if (GroupBuyActivityStatus.ACTIVE.equals(trialResult.getStatus())) {
                product.setActivityId(trialResult.getActivityId());
                product.setGroupPrice(trialResult.getGroupPrice());
                product.setTeamSize(trialResult.getTeamSize());
                product.setRemainingSeconds(trialResult.getRemainingSeconds());
            }
        } catch (Exception ignored) {
            if (product.getGroupPrice() == null) {
                product.setGroupPrice(product.getOriginPrice());
            }
        }
        return product;
    }
}














