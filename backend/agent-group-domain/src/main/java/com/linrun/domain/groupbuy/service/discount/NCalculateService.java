package com.linrun.domain.groupbuy.service.discount;

import com.linrun.domain.groupbuy.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.groupbuy.model.GroupBuyDiscount;
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
