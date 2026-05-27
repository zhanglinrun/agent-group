package com.linrun.domain.marketing.service.trial.node;

import com.linrun.domain.marketing.model.GroupBuyActivity;
import com.linrun.domain.marketing.model.GroupBuyActivityStatus;
import com.linrun.domain.marketing.model.GroupBuyDiscount;
import com.linrun.domain.marketing.model.GroupBuyMarketSku;
import com.linrun.domain.marketing.model.GroupBuyMarketTrialCommand;
import com.linrun.domain.marketing.model.GroupBuyStock;
import com.linrun.domain.marketing.model.GroupBuyTrialResult;
import com.linrun.domain.marketing.service.trial.GroupBuyMarketTrialContext;
import com.linrun.domain.support.tree.StrategyHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public class EndTrialNode implements StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> {

    @Override
    public GroupBuyTrialResult apply(GroupBuyMarketTrialCommand request, GroupBuyMarketTrialContext dynamicContext) {
        return buildResult(request, dynamicContext, LocalDateTime.now());
    }

    private GroupBuyTrialResult buildResult(GroupBuyMarketTrialCommand request,
                                            GroupBuyMarketTrialContext dynamicContext,
                                            LocalDateTime now) {
        GroupBuyMarketSku sku = dynamicContext.getSku();
        GroupBuyActivity activity = dynamicContext.getActivity();
        if (activity == null) {
            GroupBuyTrialResult result = GroupBuyTrialResult.missing(request.getGoodsId());
            result.setGoodsName(sku.getGoodsName());
            result.setOriginalPrice(normalize(sku.getOriginalPrice()));
            result.setPayPrice(normalize(sku.getOriginalPrice()));
            result.setDeductionPrice(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            result.setDataLoadMillis(dynamicContext.getDataLoadMillis());
            result.setVisible(false);
            result.setEnable(false);
            return result;
        }

        GroupBuyActivityStatus status = activity.resolveStatus(now);
        GroupBuyDiscount discount = dynamicContext.getDiscount();

        GroupBuyTrialResult result = new GroupBuyTrialResult();
        result.setGoodsId(sku.getGoodsId());
        result.setGoodsName(sku.getGoodsName());
        result.setActivityId(activity.getActivityId());
        result.setSource(request.getSource());
        result.setChannel(request.getChannel());
        result.setOriginalPrice(normalize(sku.getOriginalPrice()));
        result.setDeductionPrice(dynamicContext.getDeductionPrice());
        result.setPayPrice(dynamicContext.getPayPrice());
        result.setGroupPrice(dynamicContext.getPayPrice());
        result.setTeamSize(activity.resolveTeamSize());
        result.setTakeLimitCount(activity.getTakeLimitCount());
        result.setValidTime(activity.getValidTime());
        result.setRemainingSeconds(activity.remainingSeconds(now));
        result.setDataLoadMillis(dynamicContext.getDataLoadMillis());
        result.setStatus(status);
        result.setVisible(dynamicContext.isVisible());
        result.setEnable(dynamicContext.isEnable());
        result.setAvailable(GroupBuyActivityStatus.ACTIVE.equals(status) && dynamicContext.isVisible() && dynamicContext.isEnable());
        result.setTagId(activity.getTagId());
        result.setTagScope(activity.getTagScope());
        if (discount != null) {
            result.setDiscountId(discount.getDiscountId());
            result.setDiscountName(discount.getDiscountName());
            result.setMarketPlan(discount.getMarketPlan());
            result.setMarketExpr(discount.getMarketExpr());
        }
        fillStock(result, dynamicContext.getStock());
        result.setMessage(resolveMessage(status, dynamicContext));
        return result;
    }

    private void fillStock(GroupBuyTrialResult result, GroupBuyStock stock) {
        if (stock == null) {
            return;
        }
        result.setTotalStock(stock.getTotalStock());
        result.setAvailableStock(stock.getAvailableStock());
        result.setLockedStock(stock.getLockedStock());
        result.setPaidStock(stock.getPaidStock());
    }

    private String resolveMessage(GroupBuyActivityStatus status, GroupBuyMarketTrialContext dynamicContext) {
        if (!GroupBuyActivityStatus.ACTIVE.equals(status)) {
            return "group activity is not active";
        }
        if (!dynamicContext.isVisible()) {
            return "user cannot view this group activity";
        }
        if (!dynamicContext.isEnable()) {
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
