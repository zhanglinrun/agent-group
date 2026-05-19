package com.linrun.domain.groupbuy.service.discount;

import com.linrun.domain.groupbuy.adapter.GroupBuyMarketRepository;
import com.linrun.domain.groupbuy.model.GroupBuyDiscount;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("MJ")
public class MJCalculateService extends AbstractDiscountCalculateService {

    public MJCalculateService(GroupBuyMarketRepository groupBuyMarketRepository) {
        super(groupBuyMarketRepository);
    }

    @Override
    protected BigDecimal doCalculate(BigDecimal originalPrice, GroupBuyDiscount discount) {
        String[] segments = discount.getMarketExpr().split(",");
        if (segments.length != 2) {
            throw new AppException("GROUP_0016", "invalid full reduction expression");
        }
        BigDecimal threshold = new BigDecimal(segments[0].trim());
        BigDecimal reduction = new BigDecimal(segments[1].trim());
        if (originalPrice.compareTo(threshold) < 0) {
            return originalPrice;
        }
        return originalPrice.subtract(reduction);
    }
}
