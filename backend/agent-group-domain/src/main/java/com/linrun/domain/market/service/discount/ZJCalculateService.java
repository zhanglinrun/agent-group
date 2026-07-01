package com.linrun.domain.market.service.discount;

import com.linrun.domain.market.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.market.model.GroupBuyDiscount;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("ZJ")
public class ZJCalculateService extends AbstractDiscountCalculateService {

    public ZJCalculateService(GroupBuyMarketRepository groupBuyMarketRepository) {
        super(groupBuyMarketRepository);
    }

    @Override
    protected BigDecimal doCalculate(BigDecimal originalPrice, GroupBuyDiscount discount) {
        return originalPrice.subtract(marketExpr(discount));
    }
}















