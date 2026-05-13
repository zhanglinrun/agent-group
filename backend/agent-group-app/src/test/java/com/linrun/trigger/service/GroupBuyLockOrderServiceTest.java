package com.linrun.trigger.service;

import com.linrun.api.groupbuy.request.LockGroupBuyOrderRequest;
import com.linrun.api.groupbuy.response.LockGroupBuyOrderResponse;
import com.linrun.domain.groupbuy.adapter.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.model.GroupBuyLockResult;
import com.linrun.domain.groupbuy.model.GroupBuyLockStatus;
import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import com.linrun.domain.groupbuy.model.GroupBuyTeamStatus;
import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupBuyLockOrderServiceTest {

    private static final LocalDateTime START_TIME = LocalDateTime.now().minusMinutes(10);
    private static final LocalDateTime END_TIME = LocalDateTime.now().plusHours(1);

    @Test
    void shouldCreateNewTeamAndLockSlot() {
        FakeGroupBuyOrderLockRepository lockRepository = new FakeGroupBuyOrderLockRepository();
        GroupBuyLockOrderService service = service(lockRepository);

        LockGroupBuyOrderResponse response = service.lock(request(null, "IDEM_10001"));

        assertTrue(response.getLockId().startsWith("L"));
        assertTrue(response.getTeamId().startsWith("T"));
        assertEquals("U10001", response.getUserId());
        assertEquals("G10001", response.getGoodsId());
        assertEquals("A10001", response.getActivityId());
        assertEquals(3, response.getTeamSize());
        assertEquals(1, response.getLockedCount());
        assertEquals(2, response.getRemainingCount());
        assertEquals(GroupBuyTeamStatus.PROCESSING.name(), response.getTeamStatus());
        assertEquals(GroupBuyLockStatus.LOCKED.name(), response.getLockStatus());
        assertEquals(new BigDecimal("2099.00"), response.getLockAmount());
        assertFalse(response.isRepeated());
    }

    @Test
    void shouldJoinExistingTeamAndIncreaseLockCount() {
        FakeGroupBuyOrderLockRepository lockRepository = new FakeGroupBuyOrderLockRepository();
        GroupBuyTeam team = team("T10001", 1);
        lockRepository.teams.put(team.getTeamId(), team);
        GroupBuyLockOrderService service = service(lockRepository);

        LockGroupBuyOrderResponse response = service.lock(request("T10001", "IDEM_10002"));

        assertEquals("T10001", response.getTeamId());
        assertEquals(2, response.getLockedCount());
        assertEquals(1, response.getRemainingCount());
        assertEquals(2, lockRepository.teams.get("T10001").getLockCount());
    }

    @Test
    void shouldRejectFullTeam() {
        FakeGroupBuyOrderLockRepository lockRepository = new FakeGroupBuyOrderLockRepository();
        lockRepository.teams.put("T10001", team("T10001", 3));
        GroupBuyLockOrderService service = service(lockRepository);

        AppException exception = assertThrows(AppException.class,
                () -> service.lock(request("T10001", "IDEM_10003")));

        assertEquals("GROUP_0007", exception.getCode());
        assertEquals("拼团队伍名额已满", exception.getMessage());
    }

    @Test
    void shouldReturnExistingLockWhenIdempotentKeyRepeated() {
        FakeGroupBuyOrderLockRepository lockRepository = new FakeGroupBuyOrderLockRepository();
        GroupBuyTeam team = team("T10001", 1);
        GroupBuyOrderLock orderLock = existingLock("L10001", "IDEM_10004", "T10001");
        lockRepository.teams.put(team.getTeamId(), team);
        lockRepository.locks.put(orderLock.getIdempotentKey(), orderLock);
        GroupBuyLockOrderService service = service(lockRepository);

        LockGroupBuyOrderResponse response = service.lock(request("T10001", "IDEM_10004"));

        assertTrue(response.isRepeated());
        assertEquals("L10001", response.getLockId());
        assertEquals(1, response.getLockedCount());
        assertEquals(1, lockRepository.teams.get("T10001").getLockCount());
    }

    @Test
    void shouldRejectEndedActivity() {
        GroupBuyLockOrderService service = new GroupBuyLockOrderService(
                new FakeGuideDataRepository(),
                new FakeGroupBuyActivityRepository(activity("A10001", "G10001", LocalDateTime.now().minusHours(1))),
                new FakeGroupBuyOrderLockRepository());

        AppException exception = assertThrows(AppException.class,
                () -> service.lock(request(null, "IDEM_10005")));

        assertEquals("GROUP_0008", exception.getCode());
        assertEquals("拼团活动不可用", exception.getMessage());
    }

    private GroupBuyLockOrderService service(FakeGroupBuyOrderLockRepository lockRepository) {
        return new GroupBuyLockOrderService(
                new FakeGuideDataRepository(),
                new FakeGroupBuyActivityRepository(activity("A10001", "G10001", END_TIME)),
                lockRepository);
    }

    private LockGroupBuyOrderRequest request(String teamId, String idempotentKey) {
        LockGroupBuyOrderRequest request = new LockGroupBuyOrderRequest();
        request.setUserId("U10001");
        request.setGoodsId("G10001");
        request.setActivityId("A10001");
        request.setTeamId(teamId);
        request.setIdempotentKey(idempotentKey);
        return request;
    }

    private static GroupBuyActivity activity(String activityId, String goodsId, LocalDateTime endTime) {
        GroupBuyActivity activity = new GroupBuyActivity();
        activity.setActivityId(activityId);
        activity.setGoodsId(goodsId);
        activity.setGroupPrice(new BigDecimal("2099.00"));
        activity.setTeamSize(3);
        activity.setStartTime(START_TIME);
        activity.setEndTime(endTime);
        activity.setEnabled(true);
        return activity;
    }

    private static GroupBuyTeam team(String teamId, int lockCount) {
        GroupBuyActivity activity = activity("A10001", "G10001", END_TIME);
        GroupBuyTeam team = GroupBuyTeam.create(teamId, activity, LocalDateTime.now());
        team.setLockCount(lockCount);
        return team;
    }

    private static GroupBuyOrderLock existingLock(String lockId, String idempotentKey, String teamId) {
        return GroupBuyOrderLock.locked(
                lockId,
                idempotentKey,
                "U10001",
                teamId,
                activity("A10001", "G10001", END_TIME),
                LocalDateTime.now());
    }

    private static class FakeGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            return List.of();
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            return queryProductByGoodsId("G10001");
        }

        @Override
        public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
            GuideProduct product = new GuideProduct();
            product.setGoodsId(goodsId);
            product.setGoodsName("轻薄学习平板标准版");
            product.setOriginPrice(new BigDecimal("2399.00"));
            product.setGroupPrice(new BigDecimal("2099.00"));
            return Optional.of(product);
        }
    }

    private static class FakeGroupBuyActivityRepository implements GroupBuyActivityRepository {

        private final GroupBuyActivity activity;

        private FakeGroupBuyActivityRepository(GroupBuyActivity activity) {
            this.activity = activity;
        }

        @Override
        public Optional<GroupBuyActivity> queryByGoodsId(String goodsId) {
            return Optional.of(activity);
        }

        @Override
        public Optional<GroupBuyActivity> queryByActivityId(String activityId) {
            return Optional.of(activity);
        }
    }

    private static class FakeGroupBuyOrderLockRepository implements GroupBuyOrderLockRepository {

        private final Map<String, GroupBuyTeam> teams = new HashMap<>();
        private final Map<String, GroupBuyOrderLock> locks = new HashMap<>();

        @Override
        public Optional<GroupBuyOrderLock> queryLockByIdempotentKey(String idempotentKey) {
            return Optional.ofNullable(locks.get(idempotentKey));
        }

        @Override
        public Optional<GroupBuyTeam> queryTeamByTeamId(String teamId) {
            return Optional.ofNullable(teams.get(teamId));
        }

        @Override
        public GroupBuyLockResult lockNewTeam(GroupBuyTeam team, GroupBuyOrderLock orderLock) {
            teams.put(team.getTeamId(), team);
            locks.put(orderLock.getIdempotentKey(), orderLock);
            return new GroupBuyLockResult(orderLock, team, false);
        }

        @Override
        public GroupBuyLockResult lockExistingTeam(GroupBuyOrderLock orderLock) {
            GroupBuyTeam team = teams.get(orderLock.getTeamId());
            team.setLockCount(team.getLockCount() + 1);
            locks.put(orderLock.getIdempotentKey(), orderLock);
            return new GroupBuyLockResult(orderLock, team, false);
        }
    }
}
