package com.linrun.domain.marketing.service.discount;

import com.linrun.domain.marketing.model.GroupBuyDiscount;

import java.math.BigDecimal;

public interface DiscountCalculateService {

    BigDecimal calculate(String userId, BigDecimal originalPrice, GroupBuyDiscount discount);
}
