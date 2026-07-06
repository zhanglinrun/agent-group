package com.linrun.domain.quota.service;

import com.linrun.api.dto.UserMembershipDTO;
import com.linrun.domain.account.model.UserMembershipAccount;
import com.linrun.domain.quota.adapter.UserQuotaRepository;
import com.linrun.domain.quota.model.QuotaProduct;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 会员账户查询、扣减与开通/回滚。
 */
public class UserMembershipService {

    public static final String MEMBERSHIP_PLAN = "MEMBERSHIP_PLAN";

    private final UserQuotaRepository userQuotaRepository;

    public UserMembershipService(UserQuotaRepository userQuotaRepository) {
        this.userQuotaRepository = userQuotaRepository;
    }

    public UserMembershipDTO queryMembershipResponse(String userId) {
        return toMembershipDTO(userQuotaRepository.queryMembership(userId).orElse(null), userId);
    }

    public Optional<UserMembershipAccount> findActive(String userId) {
        LocalDateTime now = LocalDateTime.now();
        return userQuotaRepository.queryMembership(userId)
                .filter(membership -> membership.isActive(now));
    }

    public BigDecimal remainingQuota(String userId) {
        return findActive(userId)
                .map(UserMembershipAccount::remainingQuota)
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal debit(String userId, BigDecimal quotaCost, UserMembershipAccount membership) {
        if (membership == null || quotaCost.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal debit = normalizeAmount(membership.remainingQuota().min(quotaCost));
        if (debit.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        int affected = userQuotaRepository.decreaseMembershipQuota(userId, debit);
        return affected > 0 ? debit : BigDecimal.ZERO;
    }

    public boolean isMembershipPlan(QuotaProduct product) {
        return product != null && UserMembershipService.MEMBERSHIP_PLAN.equals(product.getProductType());
    }

    public UserMembershipAccount buildMembership(String userId, QuotaProduct product) {
        LocalDateTime now = LocalDateTime.now();
        UserMembershipAccount existing = userQuotaRepository.queryMembership(userId).orElse(null);
        LocalDateTime cycleStart = now;
        LocalDateTime cycleEnd = now.plusMonths(1);
        BigDecimal monthlyUsedQuota = BigDecimal.ZERO;
        if (existing != null
                && existing.isActive(now)
                && existing.getCycleEndTime() != null
                && product.getGoodsId().equals(existing.getPlanCode())) {
            cycleStart = existing.getCycleStartTime() == null ? now : existing.getCycleStartTime();
            cycleEnd = existing.getCycleEndTime().plusMonths(1);
            monthlyUsedQuota = existing.getMonthlyUsedQuota();
        }
        UserMembershipAccount membership = new UserMembershipAccount();
        membership.setUserId(userId);
        membership.setPlanCode(product.getGoodsId());
        membership.setPlanName(firstText(product.getGoodsName(), "会员套餐"));
        membership.setStatus("ACTIVE");
        membership.setMonthlyQuota(normalizeAmount(product.getQuotaAmount()));
        membership.setMonthlyUsedQuota(monthlyUsedQuota);
        membership.setCycleStartTime(cycleStart);
        membership.setCycleEndTime(cycleEnd);
        return membership;
    }

    public void saveMembership(UserMembershipAccount membership) {
        userQuotaRepository.upsertMembership(membership);
    }

    public String membershipGrantRemark(TradeOrderEntity tradeOrder, UserMembershipAccount membership) {
        String type = TradeBuyTypeEnumVO.GROUP_BUY.equals(tradeOrder.getBuyType()) ? "拼团购买" : "直接购买";
        return type + "会员开通：" + firstText(membership.getPlanName(), "会员套餐")
                + "，月额度 " + membership.getMonthlyQuota().stripTrailingZeros().toPlainString();
    }

    public void rollbackMembershipState(String userId, QuotaProduct product) {
        UserMembershipAccount membership = userQuotaRepository.queryMembership(userId).orElse(null);
        if (membership == null
                || product == null
                || !product.getGoodsId().equals(membership.getPlanCode())
                || membership.getCycleEndTime() == null) {
            return;
        }
        membership.setCycleEndTime(membership.getCycleEndTime().minusMonths(1));
        if (!membership.getCycleEndTime().isAfter(LocalDateTime.now())) {
            membership.setStatus("EXPIRED");
        }
        userQuotaRepository.upsertMembership(membership);
    }

    private UserMembershipDTO toMembershipDTO(UserMembershipAccount membership, String userId) {
        LocalDateTime now = LocalDateTime.now();
        UserMembershipDTO dto = new UserMembershipDTO();
        dto.setUserId(userId);
        dto.setPlanCode(membership == null ? "FREE" : firstText(membership.getPlanCode(), "FREE"));
        dto.setPlanName(membership == null ? "免费版" : firstText(membership.getPlanName(), "免费版"));
        dto.setStatus(membership == null ? "INACTIVE" : firstText(membership.getStatus(), "INACTIVE"));
        dto.setMonthlyQuota(normalizeAmount(membership == null ? BigDecimal.ZERO : membership.getMonthlyQuota()));
        dto.setMonthlyUsedQuota(normalizeAmount(membership == null ? BigDecimal.ZERO : membership.getMonthlyUsedQuota()));
        dto.setRemainingMonthlyQuota(normalizeAmount(membership == null ? BigDecimal.ZERO : membership.remainingQuota()));
        dto.setCycleStartTime(membership == null ? null : membership.getCycleStartTime());
        dto.setCycleEndTime(membership == null ? null : membership.getCycleEndTime());
        dto.setActive(membership != null && membership.isActive(now));
        return dto;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : safe(fallback);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
