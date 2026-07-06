package com.linrun.domain.market.service;

import com.linrun.domain.market.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.market.model.GroupBuyActivity;
import com.linrun.domain.market.model.GroupBuyActivityStatus;
import com.linrun.domain.market.model.GroupBuyTrialResult;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class GroupBuyActivityService {

    private final GroupBuyActivityRepository groupBuyActivityRepository;

    public GroupBuyActivityService(GroupBuyActivityRepository groupBuyActivityRepository) {
        this.groupBuyActivityRepository = groupBuyActivityRepository;
    }

    public GroupBuyTrialResult trial(String goodsId) {
        if (!StringUtils.hasText(goodsId)) {
            throw new AppException("0001", "额度包编号不能为空");
        }
        return groupBuyActivityRepository.queryByGoodsId(goodsId)
                .map(activity -> buildTrialResult(activity, LocalDateTime.now()))
                .orElseGet(() -> GroupBuyTrialResult.missing(goodsId));
    }

    GroupBuyTrialResult buildTrialResult(GroupBuyActivity activity, LocalDateTime now) {
        GroupBuyActivityStatus status = activity.resolveStatus(now);

        GroupBuyTrialResult result = new GroupBuyTrialResult();
        result.setGoodsId(activity.getGoodsId());
        result.setActivityId(activity.getActivityId());
        result.setGroupPrice(activity.getGroupPrice());
        result.setTeamSize(activity.resolveTeamSize());
        result.setRemainingSeconds(activity.remainingSeconds(now));
        result.setStatus(status);
        result.setAvailable(GroupBuyActivityStatus.ACTIVE.equals(status));
        result.setMessage(resolveMessage(status));
        return result;
    }

    private String resolveMessage(GroupBuyActivityStatus status) {
        return switch (status) {
            case ACTIVE -> "拼团活动可用";
            case NOT_STARTED -> "拼团活动未开始";
            case ENDED -> "拼团活动已结束";
            case DISABLED -> "拼团活动已停用";
            case MISSING -> "当前额度包没有配置拼团活动";
        };
    }
}















