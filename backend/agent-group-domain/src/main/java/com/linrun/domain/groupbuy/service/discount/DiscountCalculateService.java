package com.linrun.domain.groupbuy.service.discount;

import com.linrun.domain.groupbuy.model.GroupBuyDiscount;

import java.math.BigDecimal;

public interface DiscountCalculateService {

    BigDecimal calculate(String userId, BigDecimal originalPrice, GroupBuyDiscount discount);
}















