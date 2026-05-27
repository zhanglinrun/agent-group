package com.linrun.domain.marketing.service.trial.node;

import com.linrun.domain.conversation.adapter.GuideDataRepository;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.marketing.adapter.GroupBuyActivityRepository;
import com.linrun.domain.marketing.adapter.GroupBuyMarketRepository;
import com.linrun.domain.marketing.adapter.GroupBuyStockRepository;
import com.linrun.domain.marketing.model.GroupBuyActivity;
import com.linrun.domain.marketing.model.GroupBuyDiscount;
import com.linrun.domain.marketing.model.GroupBuyMarketSku;
import com.linrun.domain.marketing.model.GroupBuyMarketTrialCommand;
import com.linrun.domain.marketing.model.GroupBuyStock;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.Map;
import java.util.Optional;

public class MarketTrialNode extends AbstractStrategyRouter<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> {

    private final GroupBuyActivityRepository groupBuyActivityRepository;
    private final GroupBuyMarketRepository groupBuyMarketRepository;
    private final GuideDataRepository guideDataRepository;
    private final GroupBuyStockRepository groupBuyStockRepository;
    private final Executor executor;
    private final Map<String, DiscountCalculateService> discountCalculateServiceMap;
    private final StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> next;

    public MarketTrialNode(GroupBuyActivityRepository groupBuyActivityRepository,
                           GroupBuyMarketRepository groupBuyMarketRepository,
                           GuideDataRepository guideDataRepository,
                           GroupBuyStockRepository groupBuyStockRepository,
                           Map<String, DiscountCalculateService> discountCalculateServiceMap,
                           StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> next) {
        this(groupBuyActivityRepository, groupBuyMarketRepository, guideDataRepository, groupBuyStockRepository,
                ForkJoinPool.commonPool(), discountCalculateServiceMap, next);
    }

    public MarketTrialNode(GroupBuyActivityRepository groupBuyActivityRepository,
                           GroupBuyMarketRepository groupBuyMarketRepository,
                           GuideDataRepository guideDataRepository,
                           GroupBuyStockRepository groupBuyStockRepository,
                           Executor executor,
                           Map<String, DiscountCalculateService> discountCalculateServiceMap,
                           StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> next) {
        this.groupBuyActivityRepository = groupBuyActivityRepository;
        this.groupBuyMarketRepository = groupBuyMarketRepository;
        this.guideDataRepository = guideDataRepository;
        this.groupBuyStockRepository = groupBuyStockRepository == null ? GroupBuyStockRepository.noop() : groupBuyStockRepository;
        this.executor = executor == null ? ForkJoinPool.commonPool() : executor;
        this.discountCalculateServiceMap = discountCalculateServiceMap;
        this.next = next;
    }

    @Override
    protected StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> router(
            GroupBuyMarketTrialCommand request,
            GroupBuyMarketTrialContext dynamicContext) {
        long startNanos = System.nanoTime();
        CompletableFuture<GroupBuyMarketSku> skuFuture = supplyAsync(() -> resolveSku(request.getGoodsId()));
        CompletableFuture<SourceChannelSkuActivity> relationFuture =
                supplyAsync(() -> resolveSourceChannelSkuActivity(request));
        CompletableFuture<GroupBuyActivity> activityFuture =
                resolveActivityFuture(request, relationFuture);

        dynamicContext.setSku(join(skuFuture));
        dynamicContext.setSourceChannelSkuActivity(join(relationFuture));
        dynamicContext.setActivity(join(activityFuture));
        if (dynamicContext.getActivity() != null) {
            CompletableFuture<Optional<GroupBuyDiscount>> discountFuture =
                    supplyAsync(() -> resolveDiscount(dynamicContext.getActivity()));
            CompletableFuture<Optional<GroupBuyStock>> stockFuture =
                    supplyAsync(() -> resolveStock(dynamicContext.getActivity()));
            dynamicContext.setDiscount(join(discountFuture).orElse(null));
            dynamicContext.setStock(join(stockFuture).orElse(null));
            calculateDiscount(request, dynamicContext);
        }
        dynamicContext.setDataLoadMillis(elapsedMillis(startNanos));
        return next;
    }

    private CompletableFuture<GroupBuyActivity> resolveActivityFuture(
            GroupBuyMarketTrialCommand request,
            CompletableFuture<SourceChannelSkuActivity> relationFuture) {
        if (StringUtils.hasText(request.getActivityId())) {
            return supplyAsync(() -> groupBuyActivityRepository.queryByActivityId(request.getActivityId()).orElse(null));
        }
        CompletableFuture<GroupBuyActivity> relationActivityFuture = relationFuture.thenApplyAsync(relation -> {
            if (relation == null || !StringUtils.hasText(relation.getActivityId())) {
                return null;
            }
            return groupBuyActivityRepository.queryByActivityId(relation.getActivityId()).orElse(null);
        }, executor);
        CompletableFuture<GroupBuyActivity> goodsActivityFuture =
                supplyAsync(() -> groupBuyActivityRepository.queryByGoodsId(request.getGoodsId()).orElse(null));
        return relationActivityFuture.thenCombine(goodsActivityFuture,
                (relationActivity, goodsActivity) -> relationActivity == null ? goodsActivity : relationActivity);
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

    private Optional<GroupBuyStock> resolveStock(GroupBuyActivity activity) {
        if (activity == null || !StringUtils.hasText(activity.getActivityId())) {
            return Optional.empty();
        }
        return groupBuyStockRepository.queryByActivityId(activity.getActivityId());
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

    private <T> CompletableFuture<T> supplyAsync(java.util.function.Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, executor);
    }

    private <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AppException appException) {
                throw appException;
            }
            throw new AppException("GROUP_0021", "market trial data load failed");
        }
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
