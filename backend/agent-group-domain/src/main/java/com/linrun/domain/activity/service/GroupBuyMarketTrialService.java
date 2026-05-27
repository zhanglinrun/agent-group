package com.linrun.domain.activity.service;

import com.linrun.domain.agent.conversation.adapter.GuideDataRepository;
import com.linrun.domain.support.config.service.DynamicConfigService;
import com.linrun.domain.activity.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.activity.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.activity.adapter.repository.GroupBuyStockRepository;
import com.linrun.domain.activity.model.GroupBuyMarketTrialCommand;
import com.linrun.domain.activity.model.GroupBuyTrialResult;
import com.linrun.domain.activity.service.discount.DiscountCalculateService;
import com.linrun.domain.activity.service.trial.GroupBuyMarketTrialContext;
import com.linrun.domain.activity.service.trial.node.EndTrialNode;
import com.linrun.domain.activity.service.trial.node.MarketTrialNode;
import com.linrun.domain.activity.service.trial.node.SwitchTrialNode;
import com.linrun.domain.activity.service.trial.node.TagTrialNode;
import com.linrun.domain.support.tree.StrategyHandler;
import com.linrun.domain.support.tree.StrategyTree;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class GroupBuyMarketTrialService {

    private final StrategyTree<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> trialStrategyTree;

    public GroupBuyMarketTrialService(GroupBuyActivityRepository groupBuyActivityRepository,
                                      GroupBuyMarketRepository groupBuyMarketRepository,
                                      GuideDataRepository guideDataRepository,
                                      DynamicConfigService dynamicConfigService,
                                      Map<String, DiscountCalculateService> discountCalculateServiceMap) {
        this(groupBuyActivityRepository, groupBuyMarketRepository, GroupBuyStockRepository.noop(),
                guideDataRepository, dynamicConfigService, discountCalculateServiceMap);
    }

    @Autowired
    public GroupBuyMarketTrialService(GroupBuyActivityRepository groupBuyActivityRepository,
                                      GroupBuyMarketRepository groupBuyMarketRepository,
                                      GroupBuyStockRepository groupBuyStockRepository,
                                      GuideDataRepository guideDataRepository,
                                      DynamicConfigService dynamicConfigService,
                                      Map<String, DiscountCalculateService> discountCalculateServiceMap) {
        StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> endNode =
                new EndTrialNode();
        StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> tagNode =
                new TagTrialNode(groupBuyMarketRepository, endNode);
        StrategyHandler<GroupBuyMarketTrialCommand, GroupBuyMarketTrialContext, GroupBuyTrialResult> marketNode =
                new MarketTrialNode(groupBuyActivityRepository, groupBuyMarketRepository, guideDataRepository,
                        groupBuyStockRepository, discountCalculateServiceMap, tagNode);
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
