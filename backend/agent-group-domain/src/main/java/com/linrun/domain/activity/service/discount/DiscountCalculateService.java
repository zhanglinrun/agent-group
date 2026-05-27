package com.linrun.domain.activity.service.discount;

import com.linrun.domain.activity.model.GroupBuyDiscount;

import java.math.BigDecimal;

public interface DiscountCalculateService {

    BigDecimal calculate(String userId, BigDecimal originalPrice, GroupBuyDiscount discount);
}
