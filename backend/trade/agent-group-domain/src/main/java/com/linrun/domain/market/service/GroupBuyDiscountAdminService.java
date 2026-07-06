package com.linrun.domain.market.service;

import com.linrun.domain.market.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.market.model.GroupBuyDiscount;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 运营端折扣管理服务：折扣的新建、编辑、启停、删除。
 *
 * 折扣通过 marketPlan + marketExpr 描述规则：
 * - ZJ 直减：marketExpr 为直减金额，如 3 表示减 3 元；
 * - MJ 满减：marketExpr 为 满,减，如 30,7 表示满 30 减 7；
 * - ZK 折扣：marketExpr 为折扣率，如 0.8 表示八折；
 * - N  N 元购：marketExpr 为固定价格，如 1.99 表示 1.99 元购。
 */
@Service
public class GroupBuyDiscountAdminService {

    private static final DateTimeFormatter ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int DEFAULT_LIMIT = 100;
    private static final Set<String> SUPPORTED_PLANS = Set.of("ZJ", "MJ", "ZK", "N");

    private final GroupBuyMarketRepository groupBuyMarketRepository;

    public GroupBuyDiscountAdminService(GroupBuyMarketRepository groupBuyMarketRepository) {
        this.groupBuyMarketRepository = groupBuyMarketRepository;
    }

    public List<GroupBuyDiscount> listDiscounts() {
        return groupBuyMarketRepository.queryDiscountList(DEFAULT_LIMIT);
    }

    public GroupBuyDiscount saveDiscount(GroupBuyDiscount discount) {
        if (discount == null) {
            throw new AppException("GROUP_0020", "折扣内容不能为空");
        }
        String discountId = normalize(discount.getDiscountId());
        boolean isNew = !StringUtils.hasText(discountId);
        if (isNew) {
            discountId = "D" + LocalDateTime.now().format(ID_FORMATTER);
            discount.setDiscountId(discountId);
        }
        validate(discount);
        GroupBuyDiscount saved = groupBuyMarketRepository.saveDiscount(discount);
        if (saved == null) {
            throw new AppException("GROUP_0021", "保存折扣失败");
        }
        return saved;
    }

    public boolean toggleEnabled(String discountId, boolean enabled) {
        String id = normalize(discountId);
        if (!StringUtils.hasText(id)) {
            throw new AppException("GROUP_0022", "折扣编号不能为空");
        }
        if (groupBuyMarketRepository.queryDiscountByDiscountId(id).isEmpty()) {
            throw new AppException("GROUP_0023", "折扣不存在: " + id);
        }
        return groupBuyMarketRepository.updateDiscountEnabled(id, enabled);
    }

    public boolean deleteDiscount(String discountId) {
        String id = normalize(discountId);
        if (!StringUtils.hasText(id)) {
            throw new AppException("GROUP_0022", "折扣编号不能为空");
        }
        return groupBuyMarketRepository.deleteDiscount(id);
    }

    private void validate(GroupBuyDiscount discount) {
        if (!StringUtils.hasText(discount.getDiscountName())) {
            throw new AppException("GROUP_0024", "折扣名称不能为空");
        }
        String plan = normalize(discount.getMarketPlan());
        if (!SUPPORTED_PLANS.contains(plan)) {
            throw new AppException("GROUP_0025", "不支持的折扣类型: " + plan);
        }
        if (!StringUtils.hasText(discount.getMarketExpr())) {
            throw new AppException("GROUP_0026", "折扣规则不能为空");
        }
        if (discount.getDiscountType() == null) {
            discount.setDiscountType(0);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
