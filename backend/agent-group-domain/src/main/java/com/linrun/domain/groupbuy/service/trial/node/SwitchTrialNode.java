package com.linrun.domain.groupbuy.service.trial.node;

import com.linrun.domain.support.config.service.DynamicConfigService;
import com.linrun.domain.groupbuy.model.GroupBuyMarketTrialCommand;
import com.linrun.domain.groupbuy.model.GroupBuyTrialResult;
import com.linrun.domain.groupbuy.service.trial.GroupBuyMarketTrialContext;
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
            throw new AppException("DCC_0003", "拼团活动暂时不可用");
        }
        if (StringUtils.hasText(request.getUserId()) && !dynamicConfigService.isCutRange(request.getUserId())) {
            throw new AppException("DCC_0004", "当前账号暂不在活动范围内");
        }
        if (dynamicConfigService.isSourceChannelBlackIntercept(request.getSource(), request.getChannel())) {
            throw new AppException("DCC_0005", "当前渠道暂不能参加活动");
        }
        return next;
    }
}
