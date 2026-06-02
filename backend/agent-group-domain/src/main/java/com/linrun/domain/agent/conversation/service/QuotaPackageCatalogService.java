package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.activity.model.GroupBuyActivityStatus;
import com.linrun.domain.activity.model.GroupBuyTrialResult;
import com.linrun.domain.activity.service.GroupBuyActivityService;
import com.linrun.domain.agent.conversation.adapter.GuideDataRepository;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class QuotaPackageCatalogService {

    private static final String QUOTA_PACKAGE = "QUOTA_PACKAGE";

    private final GuideDataRepository guideDataRepository;
    private final GroupBuyActivityService groupBuyActivityService;

    public QuotaPackageCatalogService(GuideDataRepository guideDataRepository,
                                      GroupBuyActivityService groupBuyActivityService) {
        this.guideDataRepository = guideDataRepository;
        this.groupBuyActivityService = groupBuyActivityService;
    }

    public List<GuideProduct> listPackages(String keyword, int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 50);
        String query = StringUtils.hasText(keyword) ? keyword : "";
        return guideDataRepository.queryCandidateProducts(query, safeLimit).stream()
                .filter(this::isQuotaPackage)
                .map(this::enrichGroupBuy)
                .toList();
    }

    public GuideProduct queryPackageDetail(String goodsId) {
        if (!StringUtils.hasText(goodsId)) {
            throw new AppException("0001", "额度包编号不能为空");
        }
        GuideProduct product = guideDataRepository.queryProductByGoodsId(goodsId)
                .filter(this::isQuotaPackage)
                .orElseThrow(() -> new AppException("DATA_0003", "额度包不存在或已下架"));
        return enrichGroupBuy(product);
    }

    private boolean isQuotaPackage(GuideProduct product) {
        return product != null && QUOTA_PACKAGE.equals(product.getProductType());
    }

    private GuideProduct enrichGroupBuy(GuideProduct product) {
        if (product == null || !StringUtils.hasText(product.getGoodsId()) || groupBuyActivityService == null) {
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
