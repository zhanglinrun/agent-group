package com.linrun.domain.groupbuy.service.rules.lock;

import cn.bugstack.wrench.design.framework.link.model2.LinkArmory;
import cn.bugstack.wrench.design.framework.link.model2.chain.BusinessLinkedList;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyTeamStockRepository;
import com.linrun.domain.agent.conversation.service.GuideDecisionSnapshotValidator;
import com.linrun.types.exception.AppException;

public class GroupBuyLockRuleChain {

    private final BusinessLinkedList<GroupBuyLockContext, GroupBuyLockDynamicContext, GroupBuyLockContext> ruleFilter;

    public GroupBuyLockRuleChain(GroupBuyOrderLockRepository groupBuyOrderLockRepository,
                                 GroupBuyTeamStockRepository groupBuyTeamStockRepository,
                                 GuideDecisionSnapshotValidator guideDecisionSnapshotValidator) {
        LinkArmory<GroupBuyLockContext, GroupBuyLockDynamicContext, GroupBuyLockContext> linkArmory =
                new LinkArmory<>("group buy lock rule chain",
                        new ActivityUsabilityLockRule(),
                        new UserTakeLimitLockRule(groupBuyOrderLockRepository),
                        new DecisionSnapshotLockRule(guideDecisionSnapshotValidator),
                        new TeamStockOccupyLockRule(groupBuyOrderLockRepository, groupBuyTeamStockRepository));
        this.ruleFilter = linkArmory.getLogicLink();
    }

    public void apply(GroupBuyLockContext context) {
        try {
            ruleFilter.apply(context, new GroupBuyLockDynamicContext());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AppException("GROUP_0021", "group buy lock rule chain failed");
        }
    }
}
