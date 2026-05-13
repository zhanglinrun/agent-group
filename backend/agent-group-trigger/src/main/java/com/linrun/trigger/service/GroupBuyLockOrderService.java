package com.linrun.trigger.service;

import com.linrun.api.groupbuy.request.LockGroupBuyOrderRequest;
import com.linrun.api.groupbuy.response.LockGroupBuyOrderResponse;
import com.linrun.domain.groupbuy.adapter.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.model.GroupBuyActivityStatus;
import com.linrun.domain.groupbuy.model.GroupBuyLockResult;
import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class GroupBuyLockOrderService {

    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final GuideDataRepository guideDataRepository;
    private final GroupBuyActivityRepository groupBuyActivityRepository;
    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;

    public GroupBuyLockOrderService(GuideDataRepository guideDataRepository,
                                    GroupBuyActivityRepository groupBuyActivityRepository,
                                    GroupBuyOrderLockRepository groupBuyOrderLockRepository) {
        this.guideDataRepository = guideDataRepository;
        this.groupBuyActivityRepository = groupBuyActivityRepository;
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
    }

    public LockGroupBuyOrderResponse lock(LockGroupBuyOrderRequest request) {
        validate(request);

        GroupBuyOrderLock repeatedLock = groupBuyOrderLockRepository.queryLockByIdempotentKey(request.getIdempotentKey())
                .orElse(null);
        if (repeatedLock != null) {
            GroupBuyTeam team = groupBuyOrderLockRepository.queryTeamByTeamId(repeatedLock.getTeamId())
                    .orElseThrow(() -> new AppException("GROUP_0009", "拼团锁单数据不完整"));
            return toResponse(new GroupBuyLockResult(repeatedLock, team, true));
        }

        guideDataRepository.queryProductByGoodsId(request.getGoodsId())
                .orElseThrow(() -> new AppException("DATA_0003", "商品不存在或已下架"));
        GroupBuyActivity activity = groupBuyActivityRepository.queryByActivityId(request.getActivityId())
                .orElseThrow(() -> new AppException("GROUP_0001", "拼团活动不存在"));

        LocalDateTime now = LocalDateTime.now();
        validateActivity(request, activity, now);

        String teamId = StringUtils.hasText(request.getTeamId()) ? request.getTeamId() : nextNo("T");
        GroupBuyOrderLock orderLock = GroupBuyOrderLock.locked(
                nextNo("L"),
                request.getIdempotentKey(),
                request.getUserId(),
                teamId,
                activity,
                now);

        if (!StringUtils.hasText(request.getTeamId())) {
            GroupBuyTeam team = GroupBuyTeam.create(teamId, activity, now);
            return toResponse(groupBuyOrderLockRepository.lockNewTeam(team, orderLock));
        }

        GroupBuyTeam team = groupBuyOrderLockRepository.queryTeamByTeamId(teamId)
                .orElseThrow(() -> new AppException("GROUP_0003", "拼团队伍不存在"));
        team.assertCanJoin(activity.getActivityId(), activity.getGoodsId(), now);
        return toResponse(groupBuyOrderLockRepository.lockExistingTeam(orderLock));
    }

    private void validate(LockGroupBuyOrderRequest request) {
        if (request == null) {
            throw new AppException("0001", "锁单参数不能为空");
        }
        if (!StringUtils.hasText(request.getUserId())) {
            throw new AppException("0001", "用户编号不能为空");
        }
        if (!StringUtils.hasText(request.getGoodsId())) {
            throw new AppException("0001", "商品编号不能为空");
        }
        if (!StringUtils.hasText(request.getActivityId())) {
            throw new AppException("0001", "活动编号不能为空");
        }
        if (!StringUtils.hasText(request.getIdempotentKey())) {
            throw new AppException("0001", "幂等键不能为空");
        }
    }

    private void validateActivity(LockGroupBuyOrderRequest request, GroupBuyActivity activity, LocalDateTime now) {
        if (!request.getGoodsId().equals(activity.getGoodsId())) {
            throw new AppException("GROUP_0002", "拼团活动和商品不匹配");
        }
        if (!GroupBuyActivityStatus.ACTIVE.equals(activity.resolveStatus(now))) {
            throw new AppException("GROUP_0008", "拼团活动不可用");
        }
    }

    private LockGroupBuyOrderResponse toResponse(GroupBuyLockResult result) {
        GroupBuyOrderLock orderLock = result.getOrderLock();
        GroupBuyTeam team = result.getTeam();

        LockGroupBuyOrderResponse response = new LockGroupBuyOrderResponse();
        response.setLockId(orderLock.getLockId());
        response.setUserId(orderLock.getUserId());
        response.setGoodsId(orderLock.getGoodsId());
        response.setActivityId(orderLock.getActivityId());
        response.setTeamId(orderLock.getTeamId());
        response.setTeamSize(team.getTargetCount());
        response.setLockedCount(team.getLockCount());
        response.setRemainingCount(team.remainingCount());
        response.setTeamStatus(team.getTeamStatus().name());
        response.setLockStatus(orderLock.getLockStatus().name());
        response.setLockAmount(orderLock.getLockAmount());
        response.setLockTime(orderLock.getLockTime());
        response.setRepeated(result.isRepeated());
        return response;
    }

    private String nextNo(String prefix) {
        String timePart = LocalDateTime.now().format(ORDER_TIME_FORMATTER);
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return prefix + timePart + randomPart;
    }
}
