package com.linrun.domain.marketing.service.trial.node;

import com.linrun.domain.dcc.service.DynamicConfigService;
import com.linrun.domain.marketing.model.GroupBuyMarketTrialCommand;
import com.linrun.domain.marketing.model.GroupBuyTrialResult;
import com.linrun.domain.marketing.service.trial.GroupBuyMarketTrialContext;
import com.linrun.domain.support.tree.AbstractStrategyRouter;
import com.linrun.domain.support.tree.StrategyHandler;
import com.linrun.types.exception.AppException;
import org.springframework.util.StringUtils;

public class SwitchTrialNode extends AbstractStrategyRouter<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> {

    private final DynamicConfigService dynamicConfigService;
    private final StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> next;

    public SwitchTrialNode(DynamicConfigService dynamicConfigService,
                           StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> next) {
        this.dynamicConfigService = dynamicConfigService;
        this.next = next;
    }

    @Override
    protected StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> router(
            GroupBuyMarketTrialCommand request,
            GroupBuyMarketTrialContext dynamicContext) {
        if (dynamicConfigService.isDowngradeSwitch()) {
            throw new AppException("DCC_0003", "group buy market is downgraded");
        }
        if (StringUtils.hasText(request.getUserId()) && !dynamicConfigService.isCutRange(request.getUserId())) {
            throw new AppException("DCC_0004", "user is outside market cut range");
        }
        if (dynamicConfigService.isSourceChannelBlackIntercept(request.getSource(), request.getChannel())) {
            throw new AppException("DCC_0005", "source and channel are blocked");
        }
        return next;
    }
}
