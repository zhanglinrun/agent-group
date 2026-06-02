package com.linrun.domain.trade.service.groupbuy.lock;

import com.linrun.domain.activity.model.GroupBuyActivity;
import com.linrun.domain.activity.model.GroupBuyActivityStatus;
import com.linrun.types.exception.AppException;
import cn.bugstack.wrench.design.framework.link.model2.handler.ILogicHandler;

public class ActivityUsabilityLockRule implements ILogicHandler<GroupBuyLockContext, GroupBuyLockDynamicContext, GroupBuyLockContext> {

    @Override
    public GroupBuyLockContext apply(GroupBuyLockContext context, GroupBuyLockDynamicContext dynamicContext) throws Exception {
        GroupBuyActivity activity = context.getActivity();
        if (!context.getRequest().getGoodsId().equals(activity.getGoodsId())) {
            throw new AppException("GROUP_0002", "拼团活动和额度包不匹配");
        }
        if (!GroupBuyActivityStatus.ACTIVE.equals(activity.resolveStatus(context.getNow()))) {
            throw new AppException("GROUP_0008", "拼团活动不可用");
        }
        return next(context, dynamicContext);
    }
}
