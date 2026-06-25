package com.linrun.domain.groupbuy.service.rules.lock;

import com.linrun.domain.agent.conversation.service.QuotaOrderSnapshotValidator;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyTeamStockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.model.GroupBuyActivityStatus;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 拼团锁单前置校验，按固定顺序执行：
 * 活动可用性 → 用户参与上限 → 决策快照核对 → 队伍名额占用。
 *
 * 校验本身就是一段线性顺序，直接用普通方法调用表达，
 * 不引入额外的规则链框架；新增校验时在 {@link #apply} 里按顺序补一行即可。
 */
@Component
public class GroupBuyLockRuleChain {

    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final GroupBuyTeamStockRepository groupBuyTeamStockRepository;
    private final QuotaOrderSnapshotValidator quotaOrderSnapshotValidator;

    public GroupBuyLockRuleChain(GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                 GroupBuyTeamStockRepository groupBuyTeamStockRepository,
                                 QuotaOrderSnapshotValidator quotaOrderSnapshotValidator) {
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.groupBuyTeamStockRepository = groupBuyTeamStockRepository;
        this.quotaOrderSnapshotValidator = quotaOrderSnapshotValidator;
    }

    public void apply(GroupBuyLockContext context) {
        checkActivityUsability(context);
        checkUserTakeLimit(context);
        checkDecisionSnapshot(context);
        occupyTeamStock(context);
    }

    private void checkActivityUsability(GroupBuyLockContext context) {
        GroupBuyActivity activity = context.getActivity();
        if (!context.getRequest().getGoodsId().equals(activity.getGoodsId())) {
            throw new AppException("GROUP_0002", "拼团活动和额度包不匹配");
        }
        if (!GroupBuyActivityStatus.ACTIVE.equals(activity.resolveStatus(context.getNow()))) {
            throw new AppException("GROUP_0008", "拼团活动不可用");
        }
    }

    private void checkUserTakeLimit(GroupBuyLockContext context) {
        Integer takeLimitCount = context.getActivity().getTakeLimitCount();
        if (takeLimitCount == null || takeLimitCount <= 0) {
            return;
        }
        int count = groupBuyOrderLockRepository.countUserActivityLocks(
                context.getRequest().getUserId(),
                context.getActivity().getActivityId());
        if (count >= takeLimitCount) {
            throw new AppException("GROUP_0017", "你已达到该拼团活动的参与次数上限");
        }
    }

    private void checkDecisionSnapshot(GroupBuyLockContext context) {
        if (!StringUtils.hasText(context.getRequest().getDecisionId())) {
            return;
        }
        quotaOrderSnapshotValidator.validateGroup(
                context.getRequest().getDecisionId(),
                context.getRequest().getUserId(),
                context.getRequest().getGoodsId(),
                context.getRequest().getActivityId(),
                context.getProduct().getOriginPrice(),
                context.getActivity().getGroupPrice(),
                context.getNow());
    }

    private void occupyTeamStock(GroupBuyLockContext context) {
        String teamId = context.getRequest().getTeamId();
        if (!StringUtils.hasText(teamId)) {
            return;
        }
        GroupBuyActivity activity = context.getActivity();
        GroupBuyTeam team = groupBuyOrderLockRepository.queryTeamByTeamId(teamId)
                .orElseThrow(() -> new AppException("GROUP_0003", "拼团队伍不存在"));
        team.assertCanJoin(activity.getActivityId(), activity.getGoodsId(), context.getNow());
        boolean teamStockOccupied = groupBuyTeamStockRepository.occupyTeamStock(
                activity.getActivityId(), teamId, team.getTargetCount(), team.getValidEndTime());
        if (!teamStockOccupied) {
            throw new AppException("GROUP_0007", "拼团队伍名额已满");
        }
        context.setTeam(team);
        context.setTeamStockOccupied(true);
    }
}
