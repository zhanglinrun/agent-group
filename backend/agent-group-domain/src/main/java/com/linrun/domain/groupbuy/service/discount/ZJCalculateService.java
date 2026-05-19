package com.linrun.domain.groupbuy.service.discount;

import com.linrun.domain.groupbuy.adapter.GroupBuyMarketRepository;
import com.linrun.domain.groupbuy.model.GroupBuyDiscount;
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
