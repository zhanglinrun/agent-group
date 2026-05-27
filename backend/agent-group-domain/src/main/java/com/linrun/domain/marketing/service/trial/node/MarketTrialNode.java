package com.linrun.domain.marketing.service.trial.node;

import com.linrun.domain.conversation.adapter.GuideDataRepository;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.marketing.adapter.GroupBuyActivityRepository;
import com.linrun.domain.marketing.adapter.GroupBuyMarketRepository;
import com.linrun.domain.marketing.model.GroupBuyActivity;
import com.linrun.domain.marketing.model.GroupBuyDiscount;
import com.linrun.domain.marketing.model.GroupBuyMarketSku;
import com.linrun.domain.marketing.model.GroupBuyMarketTrialCommand;
import com.linrun.domain.marketing.model.GroupBuyTrialResult;
import com.linrun.domain.marketing.model.SourceChannelSkuActivity;
import com.linrun.domain.marketing.service.discount.DiscountCalculateService;
import com.linrun.domain.marketing.service.trial.GroupBuyMarketTrialContext;
import com.linrun.domain.support.tree.AbstractStrategyRouter;
import com.linrun.domain.support.tree.StrategyHandler;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;

public class MarketTrialNode extends AbstractStrategyRouter<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> {

    private final GroupBuyActivityRepository groupBuyActivityRepository;
    private final GroupBuyMarketRepository groupBuyMarketRepository;
    private final GuideDataRepository guideDataRepository;
    private final Map<String, DiscountCalculateService> discountCalculateServiceMap;
    private final StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> next;

    public MarketTrialNode(GroupBuyActivityRepository groupBuyActivityRepository,
                           GroupBuyMarketRepository groupBuyMarketRepository,
                           GuideDataRepository guideDataRepository,
                           Map<String, DiscountCalculateService> discountCalculateServiceMap,
                           StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> next) {
        this.groupBuyActivityRepository = groupBuyActivityRepository;
        this.groupBuyMarketRepository = groupBuyMarketRepository;
        this.guideDataRepository = guideDataRepository;
        this.discountCalculateServiceMap = discountCalculateServiceMap;
        this.next = next;
    }

    @Override
    protected StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> router(
            GroupBuyMarketTrialCommand request,
            GroupBuyMarketTrialContext dynamicContext) {
        dynamicContext.setSku(resolveSku(request.getGoodsId()));
        dynamicContext.setSourceChannelSkuActivity(resolveSourceChannelSkuActivity(request));
        dynamicContext.setActivity(resolveActivity(request, dynamicContext.getSourceChannelSkuActivity()));
        if (dynamicContext.getActivity() != null) {
            dynamicContext.setDiscount(resolveDiscount(dynamicContext.getActivity()).orElse(null));
            calculateDiscount(request, dynamicContext);
        }
        return next;
    }

    private GroupBuyMarketSku resolveSku(String goodsId) {
        return groupBuyMarketRepository.querySkuByGoodsId(goodsId)
                .or(() -> guideDataRepository.queryProductByGoodsId(goodsId).map(this::toSku))
                .orElseThrow(() -> new AppException("DATA_0003", "product not found"));
    }

    private GroupBuyMarketSku toSku(GuideProduct product) {
        GroupBuyMarketSku sku = new GroupBuyMarketSku();
        sku.setGoodsId(product.getGoodsId());
        sku.setGoodsName(product.getGoodsName());
        sku.setOriginalPrice(product.getOriginPrice());
        return sku;
    }

    private SourceChannelSkuActivity resolveSourceChannelSkuActivity(GroupBuyMarketTrialCommand request) {
        if (!StringUtils.hasText(request.getSource()) || !StringUtils.hasText(request.getChannel())) {
            return null;
        }
        return groupBuyMarketRepository.querySourceChannelSkuActivity(
                        request.getSource(), request.getChannel(), request.getGoodsId())
                .orElse(null);
    }

    private GroupBuyActivity resolveActivity(GroupBuyMarketTrialCommand request, SourceChannelSkuActivity relation) {
        if (StringUtils.hasText(request.getActivityId())) {
            return groupBuyActivityRepository.queryByActivityId(request.getActivityId()).orElse(null);
        }
        if (relation != null && StringUtils.hasText(relation.getActivityId())) {
            return groupBuyActivityRepository.queryByActivityId(relation.getActivityId()).orElse(null);
        }
        return groupBuyActivityRepository.queryByGoodsId(request.getGoodsId()).orElse(null);
    }

    private Optional<GroupBuyDiscount> resolveDiscount(GroupBuyActivity activity) {
        if (!StringUtils.hasText(activity.getDiscountId())) {
            return Optional.empty();
        }
        return groupBuyMarketRepository.queryDiscountByDiscountId(activity.getDiscountId());
    }

    private void calculateDiscount(GroupBuyMarketTrialCommand request, GroupBuyMarketTrialContext dynamicContext) {
        BigDecimal originalPrice = normalize(dynamicContext.getSku().getOriginalPrice());
        BigDecimal payPrice;
        GroupBuyDiscount discount = dynamicContext.getDiscount();
        if (discount == null) {
            payPrice = dynamicContext.getActivity().getGroupPrice() == null
                    ? originalPrice
                    : normalize(dynamicContext.getActivity().getGroupPrice());
        } else {
            DiscountCalculateService discountCalculateService = discountCalculateServiceMap.get(discount.getMarketPlan());
            if (discountCalculateService == null) {
                throw new AppException("GROUP_0015", "unsupported discount plan");
            }
            payPrice = discountCalculateService.calculate(request.getUserId(), originalPrice, discount);
        }
        dynamicContext.setPayPrice(payPrice);
        dynamicContext.setDeductionPrice(originalPrice.subtract(payPrice).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
    }

    private BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
