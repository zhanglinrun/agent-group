package com.linrun.domain.marketing.service.discount;

import com.linrun.domain.marketing.adapter.GroupBuyMarketRepository;
import com.linrun.domain.marketing.model.GroupBuyDiscount;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("N")
public class NCalculateService extends AbstractDiscountCalculateService {

    public NCalculateService(GroupBuyMarketRepository groupBuyMarketRepository) {
        super(groupBuyMarketRepository);
    }

    @Override
    protected BigDecimal doCalculate(BigDecimal originalPrice, GroupBuyDiscount discount) {
        return marketExpr(discount);
    }
}
