package com.linrun.domain.groupbuy.service.discount;

import com.linrun.domain.groupbuy.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.groupbuy.model.GroupBuyDiscount;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public abstract class AbstractDiscountCalculateService implements DiscountCalculateService {

    private static final BigDecimal MIN_PAY_PRICE = new BigDecimal("0.01");

    private final GroupBuyMarketRepository groupBuyMarketRepository;

    protected AbstractDiscountCalculateService(GroupBuyMarketRepository groupBuyMarketRepository) {
        this.groupBuyMarketRepository = groupBuyMarketRepository;
    }

    @Override
    public BigDecimal calculate(String userId, BigDecimal originalPrice, GroupBuyDiscount discount) {
        BigDecimal safeOriginalPrice = normalize(originalPrice == null ? BigDecimal.ZERO : originalPrice);
        if (discount == null) {
            return safeOriginalPrice;
        }
        if (discount.isTagDiscount()
                && StringUtils.hasText(discount.getTagId())
                && !groupBuyMarketRepository.isTagCrowdRange(discount.getTagId(), userId)) {
            return safeOriginalPrice;
        }
        return normalize(minPayPrice(doCalculate(safeOriginalPrice, discount)));
    }

    protected abstract BigDecimal doCalculate(BigDecimal originalPrice, GroupBuyDiscount discount);

    protected BigDecimal marketExpr(GroupBuyDiscount discount) {
        if (discount == null || !StringUtils.hasText(discount.getMarketExpr())) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(discount.getMarketExpr().trim());
    }

    protected BigDecimal minPayPrice(BigDecimal payPrice) {
        if (payPrice == null || payPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return MIN_PAY_PRICE;
        }
        return payPrice;
    }

    protected BigDecimal normalize(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}















