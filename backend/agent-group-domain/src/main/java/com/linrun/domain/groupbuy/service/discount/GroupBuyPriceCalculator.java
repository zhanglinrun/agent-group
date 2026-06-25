package com.linrun.domain.groupbuy.service.discount;

import com.linrun.domain.groupbuy.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.model.GroupBuyDiscount;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 拼团算价统一入口。
 *
 * 把"试算展示价"和"下单支付价"收敛到同一套逻辑，避免出现按钮显示金额与实际支付金额不一致：
 * - 活动绑定了折扣时，走 {@link DiscountCalculateService} 折扣引擎计算；
 * - 活动未绑定折扣时，回退到活动配置的 {@code groupPrice}；
 * - 两者都缺时回退到原价。
 */
@Component
public class GroupBuyPriceCalculator {

    private final GroupBuyMarketRepository groupBuyMarketRepository;
    private final Map<String, DiscountCalculateService> discountCalculateServiceMap;

    public GroupBuyPriceCalculator(GroupBuyMarketRepository groupBuyMarketRepository,
                                   Map<String, DiscountCalculateService> discountCalculateServiceMap) {
        this.groupBuyMarketRepository = groupBuyMarketRepository;
        this.discountCalculateServiceMap = discountCalculateServiceMap;
    }

    /**
     * 计算拼团支付价。
     *
     * @param userId        用户编号（用于标签人群校验）
     * @param originalPrice 商品原价
     * @param activity      拼团活动
     * @return 拼团支付价
     */
    public BigDecimal calculatePayPrice(String userId, BigDecimal originalPrice, GroupBuyActivity activity) {
        BigDecimal safeOriginal = normalize(originalPrice);
        if (activity == null) {
            return safeOriginal;
        }
        GroupBuyDiscount discount = resolveDiscount(activity);
        if (discount == null) {
            return activity.getGroupPrice() == null
                    ? safeOriginal
                    : normalize(activity.getGroupPrice());
        }
        DiscountCalculateService service = discountCalculateServiceMap.get(discount.getMarketPlan());
        if (service == null) {
            throw new AppException("GROUP_0015", "暂不支持当前优惠规则");
        }
        return service.calculate(userId, safeOriginal, discount);
    }

    /**
     * 计算扣减金额（原价 - 支付价，不低于 0）。
     */
    public BigDecimal calculateDeduction(String userId, BigDecimal originalPrice, GroupBuyActivity activity) {
        BigDecimal payPrice = calculatePayPrice(userId, originalPrice, activity);
        return normalize(originalPrice).subtract(payPrice).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private GroupBuyDiscount resolveDiscount(GroupBuyActivity activity) {
        if (!StringUtils.hasText(activity.getDiscountId())) {
            return null;
        }
        return groupBuyMarketRepository.queryDiscountByDiscountId(activity.getDiscountId()).orElse(null);
    }

    private BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
