package com.linrun.domain.groupbuy.service.rules.lock;

import cn.bugstack.wrench.design.framework.link.model2.handler.ILogicHandler;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyTeamStockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
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
                .orElseThrow(() -> new AppException("GROUP_0003", "拼团队伍不存在"));
        team.assertCanJoin(activity.getActivityId(), activity.getGoodsId(), context.getNow());
        boolean teamStockOccupied = groupBuyTeamStockRepository.occupyTeamStock(
                activity.getActivityId(), teamId, team.getTargetCount(), team.getValidEndTime());
        if (!teamStockOccupied) {
            throw new AppException("GROUP_0007", "拼团队伍名额已满");
        }
        context.setTeam(team);
        context.setTeamStockOccupied(true);
        return context;
    }
}
