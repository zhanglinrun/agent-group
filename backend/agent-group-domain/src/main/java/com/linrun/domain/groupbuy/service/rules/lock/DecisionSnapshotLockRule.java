package com.linrun.domain.groupbuy.service.rules.lock;

import cn.bugstack.wrench.design.framework.link.model2.handler.ILogicHandler;
import com.linrun.domain.agent.conversation.service.QuotaOrderSnapshotValidator;
import org.springframework.util.StringUtils;

public class DecisionSnapshotLockRule implements ILogicHandler<GroupBuyLockContext, GroupBuyLockDynamicContext, GroupBuyLockContext> {

    private final QuotaOrderSnapshotValidator QuotaOrderSnapshotValidator;

    public DecisionSnapshotLockRule(QuotaOrderSnapshotValidator QuotaOrderSnapshotValidator) {
        this.QuotaOrderSnapshotValidator = QuotaOrderSnapshotValidator;
    }

    @Override
    public GroupBuyLockContext apply(GroupBuyLockContext context, GroupBuyLockDynamicContext dynamicContext) throws Exception {
        if (!StringUtils.hasText(context.getRequest().getDecisionId())) {
            return next(context, dynamicContext);
        }
        QuotaOrderSnapshotValidator.validateGroup(
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















