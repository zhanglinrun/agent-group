package com.linrun.domain.market.service.discount;

import com.linrun.domain.market.model.GroupBuyDiscount;

import java.math.BigDecimal;

public interface DiscountCalculateService {

    BigDecimal calculate(String userId, BigDecimal originalPrice, GroupBuyDiscount discount);
}















