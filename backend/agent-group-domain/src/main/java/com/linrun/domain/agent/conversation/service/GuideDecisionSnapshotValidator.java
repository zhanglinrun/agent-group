package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.adapter.GuideDecisionSnapshotRepository;
import com.linrun.domain.agent.conversation.model.GuideDecisionSnapshot;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class GuideDecisionSnapshotValidator {

    private final GuideDecisionSnapshotRepository guideDecisionSnapshotRepository;

    public GuideDecisionSnapshotValidator(GuideDecisionSnapshotRepository guideDecisionSnapshotRepository) {
        this.guideDecisionSnapshotRepository = guideDecisionSnapshotRepository == null
                ? GuideDecisionSnapshotRepository.noop()
                : guideDecisionSnapshotRepository;
    }

    public GuideDecisionSnapshot validateDirect(String decisionId,
                                                String userId,
                                                String goodsId,
                                                BigDecimal originAmount,
                                                LocalDateTime now) {
        GuideDecisionSnapshot snapshot = baseValidate(decisionId, userId, goodsId, now);
        if (compareAmount(snapshot.getOriginAmount(), originAmount) != 0) {
            throw new AppException("GUIDE_0010", "商品价格已变化，请重新发起导购");
        }
        return snapshot;
    }

    public GuideDecisionSnapshot validateGroup(String decisionId,
                                               String userId,
                                               String goodsId,
                                               String activityId,
                                               BigDecimal originAmount,
                                               BigDecimal groupAmount,
                                               LocalDateTime now) {
        GuideDecisionSnapshot snapshot = baseValidate(decisionId, userId, goodsId, now);
        if (StringUtils.hasText(snapshot.getActivityId()) && !activityId.equals(snapshot.getActivityId())) {
            throw new AppException("GUIDE_0011", "下单活动和导购决策不一致");
        }
        if (compareAmount(snapshot.getOriginAmount(), originAmount) != 0
                || compareAmount(snapshot.getGroupAmount(), groupAmount) != 0) {
            throw new AppException("GUIDE_0010", "商品价格已变化，请重新发起导购");
        }
        return snapshot;
    }

    private GuideDecisionSnapshot baseValidate(String decisionId, String userId, String goodsId, LocalDateTime now) {
        GuideDecisionSnapshot snapshot = guideDecisionSnapshotRepository.queryByDecisionId(decisionId)
                .orElseThrow(() -> new AppException("GUIDE_0006", "导购决策不存在或已过期，请重新发起导购"));
        if (snapshot.isExpired(now)) {
            throw new AppException("GUIDE_0008", "导购报价已过期，请重新发起导购");
        }
        if (StringUtils.hasText(snapshot.getUserId()) && !userId.equals(snapshot.getUserId())) {
            throw new AppException("GUIDE_0007", "导购决策不属于当前用户");
        }
        if (!goodsId.equals(snapshot.getGoodsId())) {
            throw new AppException("GUIDE_0009", "下单商品和导购决策不一致");
        }
        return snapshot;
    }

    private int compareAmount(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right ? 0 : -1;
        }
        return left.compareTo(right);
    }
}
