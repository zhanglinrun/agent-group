package com.linrun.domain.groupbuy.service.discount;

import com.linrun.domain.groupbuy.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.groupbuy.model.GroupBuyDiscount;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service("ZK")
public class ZKCalculateService extends AbstractDiscountCalculateService {

    public ZKCalculateService(GroupBuyMarketRepository groupBuyMarketRepository) {
        super(groupBuyMarketRepository);
    }

    @Override
    protected BigDecimal doCalculate(BigDecimal originalPrice, GroupBuyDiscount discount) {
        return originalPrice.multiply(marketExpr(discount)).setScale(0, RoundingMode.DOWN);
    }
}















