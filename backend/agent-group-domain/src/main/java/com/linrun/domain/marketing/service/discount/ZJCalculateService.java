package com.linrun.domain.marketing.service.discount;

import com.linrun.domain.marketing.adapter.GroupBuyMarketRepository;
import com.linrun.domain.marketing.model.GroupBuyDiscount;
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
