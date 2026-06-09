package com.linrun.trigger.http.ops;

import com.linrun.api.dto.OpsDashboardResponse;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyMarketRepository;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyStockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.model.GroupBuyMarketSku;
import com.linrun.domain.groupbuy.model.GroupBuyStock;
import com.linrun.domain.groupbuy.model.SourceChannelSkuActivity;
import com.linrun.domain.groupbuy.tag.adapter.CrowdTagRepository;
import com.linrun.domain.groupbuy.tag.model.CrowdTag;
import com.linrun.domain.trade.adapter.repository.NotifyTaskRepository;
import com.linrun.domain.trade.model.notify.NotifyTask;
import com.linrun.trigger.config.RequestTraceContext;
import com.linrun.types.common.Response;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/ops")
public class OpsDashboardController {

    private static final int DEFAULT_LIMIT = 50;

    private final GroupBuyActivityRepository activityRepository;
    private final GroupBuyMarketRepository marketRepository;
    private final GroupBuyStockRepository stockRepository;
    private final CrowdTagRepository crowdTagRepository;
    private final NotifyTaskRepository notifyTaskRepository;

    public OpsDashboardController(GroupBuyActivityRepository activityRepository,
                                  GroupBuyMarketRepository marketRepository,
                                  GroupBuyStockRepository stockRepository,
                                  CrowdTagRepository crowdTagRepository,
                                  NotifyTaskRepository notifyTaskRepository) {
        this.activityRepository = activityRepository;
        this.marketRepository = marketRepository;
        this.stockRepository = stockRepository;
        this.crowdTagRepository = crowdTagRepository;
        this.notifyTaskRepository = notifyTaskRepository;
    }

    @GetMapping("/dashboard")
    public Response<OpsDashboardResponse> dashboard() {
        OpsDashboardResponse response = new OpsDashboardResponse();
        Map<String, GroupBuyMarketSku> skuMap = marketRepository.querySkuList(DEFAULT_LIMIT).stream()
                .collect(Collectors.toMap(GroupBuyMarketSku::getGoodsId, Function.identity(), (left, right) -> left));
        response.setActivities(activityRepository.queryActivityList(DEFAULT_LIMIT).stream()
                .map(this::toActivityItem)
                .toList());
        response.setChannels(marketRepository.querySourceChannelList(DEFAULT_LIMIT).stream()
                .map(item -> toChannelItem(item, skuMap.get(item.getGoodsId())))
                .toList());
        response.setCrowdTags(crowdTagRepository.queryTagList(DEFAULT_LIMIT).stream()
                .map(this::toCrowdTagItem)
                .toList());
        response.setStocks(stockRepository.queryStockList(DEFAULT_LIMIT).stream()
                .map(this::toStockItem)
                .toList());
        response.setNotifyTasks(notifyTaskRepository.queryRecentNotifyTaskList(DEFAULT_LIMIT).stream()
                .map(this::toNotifyTaskItem)
                .toList());
        return Response.success(response, RequestTraceContext.getRequestId());
    }

    private OpsDashboardResponse.ActivityItem toActivityItem(GroupBuyActivity activity) {
        OpsDashboardResponse.ActivityItem item = new OpsDashboardResponse.ActivityItem();
        item.setActivityId(activity.getActivityId());
        item.setActivityName(activity.getActivityName());
        item.setGoodsId(activity.getGoodsId());
        item.setGroupPrice(activity.getGroupPrice());
        item.setTeamSize(activity.resolveTeamSize());
        item.setStatus(activity.getStatus());
        item.setEnabled(activity.getEnabled());
        item.setTagId(activity.getTagId());
        item.setStartTime(activity.getStartTime());
        item.setEndTime(activity.getEndTime());
        return item;
    }

    private OpsDashboardResponse.ChannelItem toChannelItem(SourceChannelSkuActivity channel,
                                                           GroupBuyMarketSku sku) {
        OpsDashboardResponse.ChannelItem item = new OpsDashboardResponse.ChannelItem();
        item.setSource(channel.getSource());
        item.setChannel(channel.getChannel());
        item.setGoodsId(channel.getGoodsId());
        item.setActivityId(channel.getActivityId());
        if (sku != null) {
            item.setGoodsName(sku.getGoodsName());
            item.setOriginalPrice(sku.getOriginalPrice());
        }
        return item;
    }

    private OpsDashboardResponse.CrowdTagItem toCrowdTagItem(CrowdTag crowdTag) {
        OpsDashboardResponse.CrowdTagItem item = new OpsDashboardResponse.CrowdTagItem();
        item.setTagId(crowdTag.getTagId());
        item.setTagName(crowdTag.getTagName());
        item.setTagDesc(crowdTag.getTagDesc());
        item.setStatistics(crowdTag.getStatistics());
        item.setLatestBatchId(crowdTag.getLatestBatchId());
        item.setLatestJobStatus(crowdTag.getLatestJobStatus());
        item.setUpdateTime(crowdTag.getUpdateTime());
        return item;
    }

    private OpsDashboardResponse.StockItem toStockItem(GroupBuyStock stock) {
        OpsDashboardResponse.StockItem item = new OpsDashboardResponse.StockItem();
        item.setActivityId(stock.getActivityId());
        item.setGoodsId(stock.getGoodsId());
        item.setTotalStock(stock.getTotalStock());
        item.setAvailableStock(stock.getAvailableStock());
        item.setLockedStock(stock.getLockedStock());
        item.setPaidStock(stock.getPaidStock());
        item.setUpdateTime(stock.getUpdateTime());
        return item;
    }

    private OpsDashboardResponse.NotifyTaskItem toNotifyTaskItem(NotifyTask notifyTask) {
        OpsDashboardResponse.NotifyTaskItem item = new OpsDashboardResponse.NotifyTaskItem();
        item.setActivityId(notifyTask.getActivityId());
        item.setTeamId(notifyTask.getTeamId());
        item.setNotifyCategory(notifyTask.getNotifyCategory());
        item.setNotifyType(notifyTask.getNotifyType());
        item.setNotifyMq(notifyTask.getNotifyMq());
        item.setNotifyUrl(notifyTask.getNotifyUrl());
        item.setNotifyCount(notifyTask.getNotifyCount());
        item.setNotifyStatus(notifyTask.getNotifyStatus());
        item.setUuid(notifyTask.getUuid());
        item.setUpdateTime(notifyTask.getUpdateTime());
        return item;
    }
}















