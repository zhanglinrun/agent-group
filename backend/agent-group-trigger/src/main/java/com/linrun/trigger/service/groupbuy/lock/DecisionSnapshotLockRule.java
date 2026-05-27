package com.linrun.trigger.service.groupbuy.lock;

import cn.bugstack.wrench.design.framework.link.model2.handler.ILogicHandler;
import com.linrun.trigger.service.GuideDecisionSnapshotValidator;

public class DecisionSnapshotLockRule implements ILogicHandler<GroupBuyLockContext, GroupBuyLockDynamicContext, GroupBuyLockContext> {

    private final GuideDecisionSnapshotValidator guideDecisionSnapshotValidator;

    public DecisionSnapshotLockRule(GuideDecisionSnapshotValidator guideDecisionSnapshotValidator) {
        this.guideDecisionSnapshotValidator = guideDecisionSnapshotValidator;
    }

    @Override
    public GroupBuyLockContext apply(GroupBuyLockContext context, GroupBuyLockDynamicContext dynamicContext) throws Exception {
        guideDecisionSnapshotValidator.validateGroup(
                context.getRequest().getDecisionId(),
                context.getRequest().getUserId(),
                context.getRequest().getGoodsId(),
                context.getRequest().getActivityId(),
                context.getProduct().getOriginPrice(),
                context.getActivity().getGroupPrice(),
                context.getNow());
        return next(context, dynamicContext);
    }
}
