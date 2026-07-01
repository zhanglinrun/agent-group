package com.linrun.domain.market.service;

import com.linrun.domain.quota.adapter.QuotaProductRepository;
import com.linrun.domain.support.config.service.DynamicConfigService;
import com.linrun.domain.market.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.market.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.market.adapter.repository.GroupBuyStockRepository;
import com.linrun.domain.market.model.GroupBuyMarketTrialCommand;
import com.linrun.domain.market.model.GroupBuyTrialResult;
import com.linrun.domain.market.service.discount.GroupBuyPriceCalculator;
import com.linrun.domain.market.service.trial.GroupBuyMarketTrialContext;
import com.linrun.domain.market.service.trial.node.EndTrialNode;
import com.linrun.domain.market.service.trial.node.MarketTrialNode;
import com.linrun.domain.market.service.trial.node.SwitchTrialNode;
import com.linrun.domain.market.service.trial.node.TagTrialNode;
import com.linrun.domain.support.tree.StrategyHandler;
import com.linrun.domain.support.tree.StrategyTree;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class GroupBuyMarketTrialService {

    private final StrategyTree<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> trialStrategyTree;

    public GroupBuyMarketTrialService(GroupBuyActivityRepository groupBuyActivityRepository,
                                      GroupBuyMarketRepository groupBuyMarketRepository,
                                      QuotaProductRepository quotaProductRepository,
                                      DynamicConfigService dynamicConfigService,
                                      GroupBuyPriceCalculator groupBuyPriceCalculator) {
        this(groupBuyActivityRepository, groupBuyMarketRepository, GroupBuyStockRepository.noop(),
                quotaProductRepository, dynamicConfigService, groupBuyPriceCalculator);
    }

    @Autowired
    public GroupBuyMarketTrialService(GroupBuyActivityRepository groupBuyActivityRepository,
                                      GroupBuyMarketRepository groupBuyMarketRepository,
                                      GroupBuyStockRepository groupBuyStockRepository,
                                      QuotaProductRepository quotaProductRepository,
                                      DynamicConfigService dynamicConfigService,
                                      GroupBuyPriceCalculator groupBuyPriceCalculator) {
        StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> endNode =
                new EndTrialNode();
        StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> tagNode =
                new TagTrialNode(groupBuyMarketRepository, endNode);
        StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> marketNode =
                new MarketTrialNode(groupBuyActivityRepository, groupBuyMarketRepository, quotaProductRepository,
                        groupBuyStockRepository, groupBuyPriceCalculator, tagNode);
        StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> switchNode =
                new SwitchTrialNode(dynamicConfigService, marketNode);
        this.trialStrategyTree = new StrategyTree<>(switchNode);
    }

    public GroupBuyTrialResult trial(GroupBuyMarketTrialCommand command) {
        validate(command);
        return trialStrategyTree.apply(command, new GroupBuyMarketTrialContext());
    }

    private void validate(GroupBuyMarketTrialCommand command) {
        if (command == null || !StringUtils.hasText(command.getGoodsId())) {
            throw new AppException("0001", "goodsId cannot be blank");
        }
    }
}














