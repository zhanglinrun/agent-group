package com.linrun.domain.groupbuy.service;

import com.linrun.domain.groupbuy.adapter.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.model.GroupBuyActivityStatus;
import com.linrun.domain.groupbuy.model.GroupBuyTrialResult;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupBuyActivityServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 13, 16, 0, 0);

    @Test
    void shouldReturnActiveTrialResult() {
        GroupBuyActivityService service = new GroupBuyActivityService(new EmptyGroupBuyActivityRepository());

        GroupBuyTrialResult result = service.buildTrialResult(activity(true, NOW.minusMinutes(1), NOW.plusMinutes(30)), NOW);

        assertEquals("A10001", result.getActivityId());
        assertEquals("G10001", result.getGoodsId());
        assertEquals(new BigDecimal("2099.00"), result.getGroupPrice());
        assertEquals(3, result.getTeamSize());
        assertEquals(1800, result.getRemainingSeconds());
        assertEquals(GroupBuyActivityStatus.ACTIVE, result.getStatus());
        assertTrue(result.isAvailable());
        assertEquals("拼团活动可用", result.getMessage());
    }

    @Test
    void shouldReturnNotStartedWhenActivityStartsLater() {
        GroupBuyActivityService service = new GroupBuyActivityService(new EmptyGroupBuyActivityRepository());

        GroupBuyTrialResult result = service.buildTrialResult(activity(true, NOW.plusMinutes(10), NOW.plusMinutes(30)), NOW);

        assertEquals(GroupBuyActivityStatus.NOT_STARTED, result.getStatus());
        assertFalse(result.isAvailable());
        assertEquals("拼团活动未开始", result.getMessage());
    }

    @Test
    void shouldReturnEndedWhenActivityExpired() {
        GroupBuyActivityService service = new GroupBuyActivityService(new EmptyGroupBuyActivityRepository());

        GroupBuyTrialResult result = service.buildTrialResult(activity(true, NOW.minusHours(2), NOW.minusMinutes(1)), NOW);

        assertEquals(GroupBuyActivityStatus.ENDED, result.getStatus());
        assertEquals(0, result.getRemainingSeconds());
        assertFalse(result.isAvailable());
        assertEquals("拼团活动已结束", result.getMessage());
    }

    @Test
    void shouldReturnDisabledWhenActivityIsClosed() {
        GroupBuyActivityService service = new GroupBuyActivityService(new EmptyGroupBuyActivityRepository());

        GroupBuyTrialResult result = service.buildTrialResult(activity(false, NOW.minusMinutes(1), NOW.plusMinutes(30)), NOW);

        assertEquals(GroupBuyActivityStatus.DISABLED, result.getStatus());
        assertFalse(result.isAvailable());
        assertEquals("拼团活动已停用", result.getMessage());
    }

    @Test
    void shouldReturnMissingWhenGoodsHasNoActivity() {
        GroupBuyActivityService service = new GroupBuyActivityService(new EmptyGroupBuyActivityRepository());

        GroupBuyTrialResult result = service.trial("G10099");

        assertEquals("G10099", result.getGoodsId());
        assertEquals(GroupBuyActivityStatus.MISSING, result.getStatus());
        assertFalse(result.isAvailable());
        assertEquals("当前商品没有配置拼团活动", result.getMessage());
    }

    @Test
    void shouldThrowWhenGoodsIdIsBlank() {
        GroupBuyActivityService service = new GroupBuyActivityService(new EmptyGroupBuyActivityRepository());

        AppException exception = assertThrows(AppException.class, () -> service.trial(" "));

        assertEquals("0001", exception.getCode());
        assertEquals("商品编号不能为空", exception.getMessage());
    }

    private GroupBuyActivity activity(boolean enabled, LocalDateTime startTime, LocalDateTime endTime) {
        GroupBuyActivity activity = new GroupBuyActivity();
        activity.setId(1L);
        activity.setActivityId("A10001");
        activity.setGoodsId("G10001");
        activity.setGroupPrice(new BigDecimal("2099.00"));
        activity.setTeamSize(3);
        activity.setStartTime(startTime);
        activity.setEndTime(endTime);
        activity.setEnabled(enabled);
        return activity;
    }

    private static class EmptyGroupBuyActivityRepository implements GroupBuyActivityRepository {

        @Override
        public Optional<GroupBuyActivity> queryByGoodsId(String goodsId) {
            return Optional.empty();
        }

        @Override
        public Optional<GroupBuyActivity> queryByActivityId(String activityId) {
            return Optional.empty();
        }
    }
}
