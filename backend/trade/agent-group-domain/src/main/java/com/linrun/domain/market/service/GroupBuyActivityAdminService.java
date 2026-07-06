package com.linrun.domain.market.service;

import com.linrun.domain.market.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.market.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.market.adapter.repository.GroupBuyStockRepository;
import com.linrun.domain.market.model.GroupBuyActivity;
import com.linrun.domain.market.model.GroupBuyStock;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 运营端拼团活动管理服务，封装活动的创建、编辑、上下架、删除及库存联动校验。
 */
@Service
public class GroupBuyActivityAdminService {

    private static final DateTimeFormatter ACTIVITY_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int DEFAULT_LIMIT = 100;

    private final GroupBuyActivityRepository groupBuyActivityRepository;
    private final GroupBuyStockRepository groupBuyStockRepository;
    private final GroupBuyOrderLockRepository groupBuyOrderLockRepository;

    public GroupBuyActivityAdminService(GroupBuyActivityRepository groupBuyActivityRepository,
                                        GroupBuyStockRepository groupBuyStockRepository,
                                        GroupBuyOrderLockRepository groupBuyOrderLockRepository) {
        this.groupBuyActivityRepository = groupBuyActivityRepository;
        this.groupBuyStockRepository = groupBuyStockRepository;
        this.groupBuyOrderLockRepository = groupBuyOrderLockRepository;
    }

    public List<GroupBuyActivity> listActivities() {
        return groupBuyActivityRepository.queryActivityList(DEFAULT_LIMIT);
    }

    public GroupBuyActivity queryDetail(String activityId) {
        return requireActivity(activityId);
    }

    /**
     * 创建活动并初始化库存。
     *
     * @param activity  活动信息（不含 activityId，由本方法生成）
     * @param totalStock 总库存
     * @return 持久化后的活动
     */
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyActivity createActivity(GroupBuyActivity activity, int totalStock) {
        validate(activity);
        if (totalStock < 0) {
            throw new AppException("GROUP_0018", "库存不能为负数");
        }
        activity.setActivityId(nextActivityId());
        applyDefaults(activity);
        GroupBuyActivity saved = groupBuyActivityRepository.save(activity);
        groupBuyStockRepository.initStock(saved.getActivityId(), saved.getGoodsId(), totalStock);
        return saved;
    }

    /**
     * 编辑活动基本信息。库存调整单独走 {@link #updateStock}。
     */
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyActivity updateActivity(String activityId, GroupBuyActivity activity) {
        GroupBuyActivity existing = requireActivity(activityId);
        validate(activity);
        activity.setActivityId(activityId);
        activity.setId(existing.getId());
        // 启用状态走专门的上下架接口，编辑时不改 enabled
        activity.setEnabled(existing.getEnabled());
        return groupBuyActivityRepository.update(activity);
    }

    /**
     * 上下架活动。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateEnabled(String activityId, boolean enabled) {
        requireActivity(activityId);
        return groupBuyActivityRepository.updateEnabled(activityId, enabled);
    }

    /**
     * 调整活动总库存。
     */
    @Transactional(rollbackFor = Exception.class)
    public GroupBuyStock updateStock(String activityId, int totalStock) {
        requireActivity(activityId);
        if (totalStock < 0) {
            throw new AppException("GROUP_0018", "库存不能为负数");
        }
        return groupBuyStockRepository.updateTotalStock(activityId, totalStock);
    }

    /**
     * 删除活动。活动下有进行中锁单（LOCKED 或 PAID）时拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean removeActivity(String activityId) {
        requireActivity(activityId);
        int inProgress = groupBuyOrderLockRepository.countInProgressLocksByActivityId(activityId);
        if (inProgress > 0) {
            throw new AppException("GROUP_0019", "活动下存在进行中的订单，无法删除");
        }
        boolean removed = groupBuyActivityRepository.removeByActivityId(activityId);
        groupBuyStockRepository.removeByActivityId(activityId);
        return removed;
    }

    private GroupBuyActivity requireActivity(String activityId) {
        if (!StringUtils.hasText(activityId)) {
            throw new AppException("GROUP_0001", "拼团活动不存在");
        }
        return groupBuyActivityRepository.queryByActivityId(activityId)
                .orElseThrow(() -> new AppException("GROUP_0001", "拼团活动不存在"));
    }

    private void validate(GroupBuyActivity activity) {
        if (activity == null) {
            throw new AppException("GROUP_0020", "活动信息不能为空");
        }
        if (!StringUtils.hasText(activity.getActivityName())) {
            throw new AppException("GROUP_0020", "活动名称不能为空");
        }
        if (!StringUtils.hasText(activity.getGoodsId())) {
            throw new AppException("GROUP_0020", "关联额度包不能为空");
        }
        if (activity.getGroupPrice() == null || activity.getGroupPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException("GROUP_0020", "团价不能为空且不能为负数");
        }
        if (activity.getTeamSize() == null || activity.getTeamSize() < 1) {
            throw new AppException("GROUP_0020", "成团人数不能小于 1");
        }
        if (activity.getStartTime() == null || activity.getEndTime() == null
                || !activity.getEndTime().isAfter(activity.getStartTime())) {
            throw new AppException("GROUP_0020", "活动有效期不合法");
        }
    }

    private void applyDefaults(GroupBuyActivity activity) {
        if (activity.getTarget() == null || activity.getTarget() < 1) {
            activity.setTarget(activity.getTeamSize());
        }
        if (activity.getValidTime() == null || activity.getValidTime() < 1) {
            activity.setValidTime(1440);
        }
        if (activity.getTakeLimitCount() == null || activity.getTakeLimitCount() < 1) {
            activity.setTakeLimitCount(1);
        }
        if (activity.getGroupType() == null) {
            activity.setGroupType(0);
        }
        if (activity.getStatus() == null) {
            activity.setStatus(1);
        }
        if (activity.getEnabled() == null) {
            activity.setEnabled(true);
        }
    }

    private String nextActivityId() {
        return "A" + LocalDateTime.now().format(ACTIVITY_ID_FORMATTER)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
