package com.linrun.domain.activity.service.discount;

import com.linrun.domain.activity.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.activity.model.GroupBuyDiscount;
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
