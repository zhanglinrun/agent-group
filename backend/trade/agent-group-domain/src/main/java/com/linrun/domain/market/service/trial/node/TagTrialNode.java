package com.linrun.domain.market.service.trial.node;

import com.linrun.domain.market.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.market.model.GroupBuyActivity;
import com.linrun.domain.market.model.GroupBuyMarketTrialCommand;
import com.linrun.domain.market.model.GroupBuyTrialResult;
import com.linrun.domain.market.service.trial.GroupBuyMarketTrialContext;
import com.linrun.domain.support.tree.AbstractStrategyRouter;
import com.linrun.domain.support.tree.StrategyHandler;
import org.springframework.util.StringUtils;

public class TagTrialNode extends AbstractStrategyRouter<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> {

    private final GroupBuyMarketRepository groupBuyMarketRepository;
    private final StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> next;

    public TagTrialNode(GroupBuyMarketRepository groupBuyMarketRepository,
                        StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> next) {
        this.groupBuyMarketRepository = groupBuyMarketRepository;
        this.next = next;
    }

    @Override
    protected StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> router(
            GroupBuyMarketTrialCommand request,
            GroupBuyMarketTrialContext dynamicContext) {
        GroupBuyActivity activity = dynamicContext.getActivity();
        if (activity == null || !StringUtils.hasText(activity.getTagId())) {
            dynamicContext.setVisible(true);
            dynamicContext.setEnable(true);
            return next;
        }
        boolean within = groupBuyMarketRepository.isTagCrowdRange(activity.getTagId(), request.getUserId());
        dynamicContext.setVisible(resolveVisibleDefault(activity.getTagScope()) || within);
        dynamicContext.setEnable(resolveEnableDefault(activity.getTagScope()) || within);
        return next;
    }

    private boolean resolveVisibleDefault(String tagScope) {
        if (!StringUtils.hasText(tagScope)) {
            return true;
        }
        String[] scopes = tagScope.split(",");
        return scopes.length == 0 || !"1".equals(scopes[0].trim());
    }

    private boolean resolveEnableDefault(String tagScope) {
        if (!StringUtils.hasText(tagScope)) {
            return true;
        }
        String[] scopes = tagScope.split(",");
        if (scopes.length == 1) {
            return !"2".equals(scopes[0].trim());
        }
        return scopes.length < 2 || !"2".equals(scopes[1].trim());
    }
}















