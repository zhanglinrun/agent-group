package com.linrun.domain.groupbuy.service;

import com.linrun.domain.dcc.service.DynamicConfigService;
import com.linrun.domain.groupbuy.adapter.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.adapter.GroupBuyMarketRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.model.GroupBuyActivityStatus;
import com.linrun.domain.groupbuy.model.GroupBuyDiscount;
import com.linrun.domain.groupbuy.model.GroupBuyMarketSku;
import com.linrun.domain.groupbuy.model.GroupBuyMarketTrialCommand;
import com.linrun.domain.groupbuy.model.GroupBuyTrialResult;
import com.linrun.domain.groupbuy.model.SourceChannelSkuActivity;
import com.linrun.domain.groupbuy.service.discount.DiscountCalculateService;
import com.linrun.domain.groupbuy.service.trial.GroupBuyMarketTrialContext;
import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class GroupBuyMarketTrialService {

    private final GroupBuyActivityRepository groupBuyActivityRepository;
    private final GroupBuyMarketRepository groupBuyMarketRepository;
    private final GuideDataRepository guideDataRepository;
    private final DynamicConfigService dynamicConfigService;
    private final Map<String, DiscountCalculateService> discountCalculateServiceMap;

    public GroupBuyMarketTrialService(GroupBuyActivityRepository groupBuyActivityRepository,
                                      GroupBuyMarketRepository groupBuyMarketRepository,
                                      GuideDataRepository guideDataRepository,
                                      DynamicConfigService dynamicConfigService,
                                      Map<String, DiscountCalculateService> discountCalculateServiceMap) {
        this.groupBuyActivityRepository = groupBuyActivityRepository;
        this.groupBuyMarketRepository = groupBuyMarketRepository;
        this.guideDataRepository = guideDataRepository;
        this.dynamicConfigService = dynamicConfigService;
        this.discountCalculateServiceMap = discountCalculateServiceMap;
    }

    public GroupBuyTrialResult trial(GroupBuyMarketTrialCommand command) {
        validate(command);
        GroupBuyMarketTrialContext context = new GroupBuyMarketTrialContext();
        switchNode(command);
        marketNode(command, context);
        tagNode(command, context);
        return endNode(command, context, LocalDateTime.now());
    }

    private void validate(GroupBuyMarketTrialCommand command) {
        if (command == null || !StringUtils.hasText(command.getGoodsId())) {
            throw new AppException("0001", "goodsId cannot be blank");
        }
    }

    private void switchNode(GroupBuyMarketTrialCommand command) {
        if (dynamicConfigService.isDowngradeSwitch()) {
            throw new AppException("DCC_0003", "group buy market is downgraded");
        }
        if (StringUtils.hasText(command.getUserId()) && !dynamicConfigService.isCutRange(command.getUserId())) {
            throw new AppException("DCC_0004", "user is outside market cut range");
        }
        if (dynamicConfigService.isSourceChannelBlackIntercept(command.getSource(), command.getChannel())) {
            throw new AppException("DCC_0005", "source and channel are blocked");
        }
    }

    private void marketNode(GroupBuyMarketTrialCommand command, GroupBuyMarketTrialContext context) {
        context.setSku(resolveSku(command.getGoodsId()));
        context.setSourceChannelSkuActivity(resolveSourceChannelSkuActivity(command));
        context.setActivity(resolveActivity(command, context.getSourceChannelSkuActivity()));
        if (context.getActivity() == null) {
            return;
        }
        context.setDiscount(resolveDiscount(context.getActivity()).orElse(null));
        calculateDiscount(command, context);
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

    private SourceChannelSkuActivity resolveSourceChannelSkuActivity(GroupBuyMarketTrialCommand command) {
        if (!StringUtils.hasText(command.getSource()) || !StringUtils.hasText(command.getChannel())) {
            return null;
        }
        return groupBuyMarketRepository.querySourceChannelSkuActivity(
                        command.getSource(), command.getChannel(), command.getGoodsId())
                .orElse(null);
    }

    private GroupBuyActivity resolveActivity(GroupBuyMarketTrialCommand command, SourceChannelSkuActivity relation) {
        if (StringUtils.hasText(command.getActivityId())) {
            return groupBuyActivityRepository.queryByActivityId(command.getActivityId()).orElse(null);
        }
        if (relation != null && StringUtils.hasText(relation.getActivityId())) {
            return groupBuyActivityRepository.queryByActivityId(relation.getActivityId()).orElse(null);
        }
        return groupBuyActivityRepository.queryByGoodsId(command.getGoodsId()).orElse(null);
    }

    private Optional<GroupBuyDiscount> resolveDiscount(GroupBuyActivity activity) {
        if (!StringUtils.hasText(activity.getDiscountId())) {
            return Optional.empty();
        }
        return groupBuyMarketRepository.queryDiscountByDiscountId(activity.getDiscountId());
    }

    private void calculateDiscount(GroupBuyMarketTrialCommand command, GroupBuyMarketTrialContext context) {
        BigDecimal originalPrice = normalize(context.getSku().getOriginalPrice());
        BigDecimal payPrice;
        GroupBuyDiscount discount = context.getDiscount();
        if (discount == null) {
            payPrice = context.getActivity().getGroupPrice() == null
                    ? originalPrice
                    : normalize(context.getActivity().getGroupPrice());
        } else {
            DiscountCalculateService discountCalculateService = discountCalculateServiceMap.get(discount.getMarketPlan());
            if (discountCalculateService == null) {
                throw new AppException("GROUP_0015", "unsupported discount plan");
            }
            payPrice = discountCalculateService.calculate(command.getUserId(), originalPrice, discount);
        }
        context.setPayPrice(payPrice);
        context.setDeductionPrice(originalPrice.subtract(payPrice).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
    }

    private void tagNode(GroupBuyMarketTrialCommand command, GroupBuyMarketTrialContext context) {
        GroupBuyActivity activity = context.getActivity();
        if (activity == null || !StringUtils.hasText(activity.getTagId())) {
            context.setVisible(true);
            context.setEnable(true);
            return;
        }
        boolean within = groupBuyMarketRepository.isTagCrowdRange(activity.getTagId(), command.getUserId());
        context.setVisible(resolveVisibleDefault(activity.getTagScope()) || within);
        context.setEnable(resolveEnableDefault(activity.getTagScope()) || within);
    }

    private boolean resolveVisibleDefault(String tagScope) {
        if (!StringUtils.hasText(tagScope)) {
            return true;
        }
        String[] scopes = tagScope.split(",");
        return scopes.length == 0 || !"1".equals(scopes[0].trim());
    }

    private boolean resolveEnableDefault(String tagScope) {
        if (!StringUtils.hasText(tagScope)) {
            return true;
        }
        String[] scopes = tagScope.split(",");
        if (scopes.length == 1) {
            return !"2".equals(scopes[0].trim());
        }
        return scopes.length < 2 || !"2".equals(scopes[1].trim());
    }

    private GroupBuyTrialResult endNode(GroupBuyMarketTrialCommand command,
                                        GroupBuyMarketTrialContext context,
                                        LocalDateTime now) {
        GroupBuyMarketSku sku = context.getSku();
        GroupBuyActivity activity = context.getActivity();
        if (activity == null) {
            GroupBuyTrialResult result = GroupBuyTrialResult.missing(command.getGoodsId());
            result.setGoodsName(sku.getGoodsName());
            result.setOriginalPrice(normalize(sku.getOriginalPrice()));
            result.setPayPrice(normalize(sku.getOriginalPrice()));
            result.setDeductionPrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            result.setVisible(false);
            result.setEnable(false);
            return result;
        }

        GroupBuyActivityStatus status = activity.resolveStatus(now);
        GroupBuyDiscount discount = context.getDiscount();

        GroupBuyTrialResult result = new GroupBuyTrialResult();
        result.setGoodsId(sku.getGoodsId());
        result.setGoodsName(sku.getGoodsName());
        result.setActivityId(activity.getActivityId());
        result.setSource(command.getSource());
        result.setChannel(command.getChannel());
        result.setOriginalPrice(normalize(sku.getOriginalPrice()));
        result.setDeductionPrice(context.getDeductionPrice());
        result.setPayPrice(context.getPayPrice());
        result.setGroupPrice(context.getPayPrice());
        result.setTeamSize(activity.resolveTeamSize());
        result.setTakeLimitCount(activity.getTakeLimitCount());
        result.setValidTime(activity.getValidTime());
        result.setRemainingSeconds(activity.remainingSeconds(now));
        result.setStatus(status);
        result.setVisible(context.isVisible());
        result.setEnable(context.isEnable());
        result.setAvailable(GroupBuyActivityStatus.ACTIVE.equals(status) && context.isVisible() && context.isEnable());
        result.setTagId(activity.getTagId());
        result.setTagScope(activity.getTagScope());
        if (discount != null) {
            result.setDiscountId(discount.getDiscountId());
            result.setDiscountName(discount.getDiscountName());
            result.setMarketPlan(discount.getMarketPlan());
            result.setMarketExpr(discount.getMarketExpr());
        }
        result.setMessage(resolveMessage(status, context));
        return result;
    }

    private String resolveMessage(GroupBuyActivityStatus status, GroupBuyMarketTrialContext context) {
        if (!GroupBuyActivityStatus.ACTIVE.equals(status)) {
            return "group activity is not active";
        }
        if (!context.isVisible()) {
            return "user cannot view this group activity";
        }
        if (!context.isEnable()) {
            return "user cannot join this group activity";
        }
        return "group activity available";
    }

    private BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
