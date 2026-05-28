package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.activity.model.GroupBuyActivityStatus;
import com.linrun.domain.activity.model.GroupBuyTrialResult;
import com.linrun.domain.activity.service.GroupBuyActivityService;
import com.linrun.domain.agent.conversation.adapter.ProductRpcClient;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ProductCatalogService {

    private final ProductRpcClient productRpcClient;
    private final GroupBuyActivityService groupBuyActivityService;

    public ProductCatalogService(ProductRpcClient productRpcClient,
                                 GroupBuyActivityService groupBuyActivityService) {
        this.productRpcClient = productRpcClient;
        this.groupBuyActivityService = groupBuyActivityService;
    }

    public List<GuideProduct> listProducts(String keyword, int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 50);
        String query = StringUtils.hasText(keyword) ? keyword : "";
        return productRpcClient.queryProducts(query, safeLimit).stream()
                .map(this::enrichGroupBuy)
                .toList();
    }

    public GuideProduct queryProductDetail(String goodsId) {
        if (!StringUtils.hasText(goodsId)) {
            throw new AppException("0001", "goodsId cannot be blank");
        }
        return productRpcClient.queryProductByGoodsId(goodsId)
                .map(this::enrichGroupBuy)
                .orElseThrow(() -> new AppException("DATA_0003", "product not found"));
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
