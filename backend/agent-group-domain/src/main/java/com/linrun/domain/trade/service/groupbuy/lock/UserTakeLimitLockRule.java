package com.linrun.domain.trade.service.groupbuy.lock;

import cn.bugstack.wrench.design.framework.link.model2.handler.ILogicHandler;
import com.linrun.domain.activity.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.activity.model.GroupBuyActivity;
import com.linrun.types.exception.AppException;

public class UserTakeLimitLockRule implements ILogicHandler<GroupBuyLockContext, GroupBuyLockDynamicContext, GroupBuyLockContext> {

    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;

    public UserTakeLimitLockRule(GroupBuyOrderLockRepository groupBuyOrderLockRepository) {
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
    }

    @Override
    public GroupBuyLockContext apply(GroupBuyLockContext context, GroupBuyLockDynamicContext dynamicContext) throws Exception {
        GroupBuyActivity activity = context.getActivity();
        Integer takeLimitCount = activity.getTakeLimitCount();
        int count = groupBuyOrderLockRepository.countUserActivityLocks(
                context.getRequest().getUserId(),
                activity.getActivityId());
        dynamicContext.setUserTakeOrderCount(count);
        if (takeLimitCount == null || takeLimitCount <= 0) {
            return next(context, dynamicContext);
        }
        if (count >= takeLimitCount) {
            throw new AppException("GROUP_0017", "user group buy take limit reached");
        }
        return next(context, dynamicContext);
    }
}
