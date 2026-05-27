package com.linrun.domain.trade.service.groupbuy.lock;

import cn.bugstack.wrench.design.framework.link.model2.handler.ILogicHandler;
import com.linrun.domain.activity.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.activity.adapter.repository.GroupBuyTeamStockRepository;
import com.linrun.domain.activity.model.GroupBuyActivity;
import com.linrun.domain.activity.model.GroupBuyTeam;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

public class TeamStockOccupyLockRule implements ILogicHandler<GroupBuyLockContext, GroupBuyLockDynamicContext, GroupBuyLockContext> {

    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;
    private final GroupBuyTeamStockRepository groupBuyTeamStockRepository;

    public TeamStockOccupyLockRule(GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                   GroupBuyTeamStockRepository groupBuyTeamStockRepository) {
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
        this.groupBuyTeamStockRepository = groupBuyTeamStockRepository;
    }

    @Override
    public GroupBuyLockContext apply(GroupBuyLockContext context, GroupBuyLockDynamicContext dynamicContext) {
        String teamId = context.getRequest().getTeamId();
        if (!StringUtils.hasText(teamId)) {
            return context;
        }

        GroupBuyActivity activity = context.getActivity();
        GroupBuyTeam team = groupBuyOrderLockRepository.queryTeamByTeamId(teamId)
                .orElseThrow(() -> new AppException("GROUP_0003", "group team not found"));
        team.assertCanJoin(activity.getActivityId(), activity.getGoodsId(), context.getNow());
        boolean teamStockOccupied = groupBuyTeamStockRepository.occupyTeamStock(
                activity.getActivityId(), teamId, team.getTargetCount(), team.getValidEndTime());
        if (!teamStockOccupied) {
            throw new AppException("GROUP_0007", "group team slot is full");
        }
        context.setTeam(team);
        context.setTeamStockOccupied(true);
        return context;
    }
}
