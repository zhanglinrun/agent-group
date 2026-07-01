package com.linrun.domain.market.service;

import com.linrun.domain.market.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.market.adapter.repository.GroupBuyOrderLockRepository;
import com.linrun.domain.market.adapter.repository.GroupBuyStockRepository;
import com.linrun.domain.market.model.GroupBuyActivity;
import com.linrun.domain.market.model.GroupBuyLockResult;
import com.linrun.domain.market.model.GroupBuyOrderLock;
import com.linrun.domain.market.model.GroupBuySettlementResult;
import com.linrun.domain.market.model.GroupBuyStock;
import com.linrun.domain.market.model.GroupBuyTeam;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupBuyActivityAdminServiceTest {

    private static final LocalDateTime START = LocalDateTime.now().plusMinutes(1);
    private static final LocalDateTime END = LocalDateTime.now().plusDays(7);

    @Test
    void shouldCreateActivityAndInitStock() {
        FakeActivityRepository activityRepo = new FakeActivityRepository();
        FakeStockRepository stockRepo = new FakeStockRepository();
        GroupBuyActivityAdminService service = new GroupBuyActivityAdminService(
                activityRepo, stockRepo, GroupBuyOrderLockRepository.noop());

        GroupBuyActivity activity = validActivity();
        GroupBuyActivity saved = service.createActivity(activity, 50);

        assertNotNull(saved.getActivityId());
        assertEquals(50, stockRepo.savedStock.getTotalStock());
        assertEquals(50, stockRepo.savedStock.getAvailableStock());
        assertEquals(0, stockRepo.savedStock.getLockedStock());
        assertTrue(activityRepo.exists(saved.getActivityId()));
    }

    @Test
    void shouldRejectCreateWithInvalidPrice() {
        GroupBuyActivityAdminService service = new GroupBuyActivityAdminService(
                new FakeActivityRepository(), new FakeStockRepository(), GroupBuyOrderLockRepository.noop());

        GroupBuyActivity activity = validActivity();
        activity.setGroupPrice(new BigDecimal("-1"));

        AppException ex = assertThrows(AppException.class, () -> service.createActivity(activity, 10));
        assertEquals("GROUP_0020", ex.getCode());
    }

    @Test
    void shouldRejectCreateWithNegativeStock() {
        GroupBuyActivityAdminService service = new GroupBuyActivityAdminService(
                new FakeActivityRepository(), new FakeStockRepository(), GroupBuyOrderLockRepository.noop());

        AppException ex = assertThrows(AppException.class,
                () -> service.createActivity(validActivity(), -1));
        assertEquals("GROUP_0018", ex.getCode());
    }

    @Test
    void shouldUpdateActivityBasicInfo() {
        FakeActivityRepository activityRepo = new FakeActivityRepository();
        activityRepo.save(validActivityWithId("A20001"));
        GroupBuyActivityAdminService service = new GroupBuyActivityAdminService(
                activityRepo, new FakeStockRepository(), GroupBuyOrderLockRepository.noop());

        GroupBuyActivity update = validActivity();
        update.setActivityName("新名称");
        update.setGroupPrice(new BigDecimal("99.00"));
        GroupBuyActivity updated = service.updateActivity("A20001", update);

        assertEquals("A20001", updated.getActivityId());
        assertEquals("新名称", updated.getActivityName());
        assertEquals(new BigDecimal("99.00"), updated.getGroupPrice());
    }

    @Test
    void shouldNotChangeEnabledOnUpdate() {
        FakeActivityRepository activityRepo = new FakeActivityRepository();
        GroupBuyActivity existing = validActivityWithId("A20001");
        existing.setEnabled(false);
        activityRepo.save(existing);
        GroupBuyActivityAdminService service = new GroupBuyActivityAdminService(
                activityRepo, new FakeStockRepository(), GroupBuyOrderLockRepository.noop());

        GroupBuyActivity update = validActivity();
        update.setEnabled(true);
        GroupBuyActivity updated = service.updateActivity("A20001", update);

        assertFalse(updated.getEnabled());
    }

    @Test
    void shouldToggleEnabled() {
        FakeActivityRepository activityRepo = new FakeActivityRepository();
        activityRepo.save(validActivityWithId("A20001"));
        GroupBuyActivityAdminService service = new GroupBuyActivityAdminService(
                activityRepo, new FakeStockRepository(), GroupBuyOrderLockRepository.noop());

        assertTrue(service.updateEnabled("A20001", false));
        assertFalse(activityRepo.findByActivityId("A20001").getEnabled());
    }

    @Test
    void shouldUpdateStock() {
        FakeActivityRepository activityRepo = new FakeActivityRepository();
        activityRepo.save(validActivityWithId("A20001"));
        FakeStockRepository stockRepo = new FakeStockRepository();
        stockRepo.savedStock = stock("A20001", 100, 100, 0, 0);
        GroupBuyActivityAdminService service = new GroupBuyActivityAdminService(
                activityRepo, stockRepo, GroupBuyOrderLockRepository.noop());

        GroupBuyStock updated = service.updateStock("A20001", 200);
        assertEquals(200, updated.getTotalStock());
        assertEquals(200, updated.getAvailableStock());
    }

    @Test
    void shouldRejectStockShrinkBelowLockedAndPaid() {
        FakeActivityRepository activityRepo = new FakeActivityRepository();
        activityRepo.save(validActivityWithId("A20001"));
        FakeStockRepository stockRepo = new FakeStockRepository();
        stockRepo.savedStock = stock("A20001", 100, 50, 30, 20);
        GroupBuyActivityAdminService service = new GroupBuyActivityAdminService(
                activityRepo, stockRepo, GroupBuyOrderLockRepository.noop());

        AppException ex = assertThrows(AppException.class, () -> service.updateStock("A20001", 40));
        assertEquals("GROUP_0017", ex.getCode());
    }

    @Test
    void shouldRemoveActivityAndClearStock() {
        FakeActivityRepository activityRepo = new FakeActivityRepository();
        activityRepo.save(validActivityWithId("A20001"));
        FakeStockRepository stockRepo = new FakeStockRepository();
        stockRepo.savedStock = stock("A20001", 100, 100, 0, 0);
        GroupBuyActivityAdminService service = new GroupBuyActivityAdminService(
                activityRepo, stockRepo, GroupBuyOrderLockRepository.noop());

        assertTrue(service.removeActivity("A20001"));
        assertFalse(activityRepo.exists("A20001"));
        assertTrue(stockRepo.removedActivityIds.contains("A20001"));
    }

    @Test
    void shouldRejectRemoveWhenInProgressLocksExist() {
        FakeActivityRepository activityRepo = new FakeActivityRepository();
        activityRepo.save(validActivityWithId("A20001"));
        GroupBuyActivityAdminService service = new GroupBuyActivityAdminService(
                activityRepo, new FakeStockRepository(), new FakeOrderLockRepository(2));

        AppException ex = assertThrows(AppException.class, () -> service.removeActivity("A20001"));
        assertEquals("GROUP_0019", ex.getCode());
        assertTrue(activityRepo.exists("A20001"));
    }

    @Test
    void shouldRejectDetailForMissingActivity() {
        GroupBuyActivityAdminService service = new GroupBuyActivityAdminService(
                new FakeActivityRepository(), new FakeStockRepository(), GroupBuyOrderLockRepository.noop());

        AppException ex = assertThrows(AppException.class, () -> service.queryDetail("missing"));
        assertEquals("GROUP_0001", ex.getCode());
    }

    private GroupBuyActivity validActivity() {
        GroupBuyActivity activity = new GroupBuyActivity();
        activity.setActivityName("测试活动");
        activity.setGoodsId("G10001");
        activity.setGroupPrice(new BigDecimal("16.90"));
        activity.setTeamSize(3);
        activity.setStartTime(START);
        activity.setEndTime(END);
        return activity;
    }

    private GroupBuyActivity validActivityWithId(String activityId) {
        GroupBuyActivity activity = validActivity();
        activity.setActivityId(activityId);
        return activity;
    }

    private static GroupBuyStock stock(String activityId, int total, int available, int locked, int paid) {
        GroupBuyStock stock = new GroupBuyStock();
        stock.setActivityId(activityId);
        stock.setGoodsId("G10001");
        stock.setTotalStock(total);
        stock.setAvailableStock(available);
        stock.setLockedStock(locked);
        stock.setPaidStock(paid);
        return stock;
    }

    private static class FakeActivityRepository implements GroupBuyActivityRepository {
        private final Map<String, GroupBuyActivity> store = new HashMap<>();

        @Override
        public Optional<GroupBuyActivity> queryByGoodsId(String goodsId) {
            return store.values().stream().filter(a -> a.getGoodsId().equals(goodsId)).findFirst();
        }

        @Override
        public Optional<GroupBuyActivity> queryByActivityId(String activityId) {
            return Optional.ofNullable(store.get(activityId));
        }

        @Override
        public List<GroupBuyActivity> queryActivityList(int limit) {
            return new ArrayList<>(store.values());
        }

        @Override
        public GroupBuyActivity save(GroupBuyActivity activity) {
            store.put(activity.getActivityId(), activity);
            return activity;
        }

        @Override
        public GroupBuyActivity update(GroupBuyActivity activity) {
            store.put(activity.getActivityId(), activity);
            return activity;
        }

        @Override
        public boolean updateEnabled(String activityId, boolean enabled) {
            GroupBuyActivity activity = store.get(activityId);
            if (activity == null) {
                return false;
            }
            activity.setEnabled(enabled);
            return true;
        }

        @Override
        public boolean removeByActivityId(String activityId) {
            return store.remove(activityId) != null;
        }

        boolean exists(String activityId) {
            return store.containsKey(activityId);
        }

        GroupBuyActivity findByActivityId(String activityId) {
            return store.get(activityId);
        }
    }

    private static class FakeStockRepository implements GroupBuyStockRepository {
        private GroupBuyStock savedStock;
        private final List<String> removedActivityIds = new ArrayList<>();

        @Override
        public GroupBuyStock lockStock(String activityId, String goodsId, String orderId, String teamId) {
            return savedStock;
        }

        @Override
        public GroupBuyStock markPaidStock(String activityId, String goodsId, String orderId, String teamId) {
            return savedStock;
        }

        @Override
        public GroupBuyStock releaseLockedStock(String activityId, String goodsId, String orderId, String teamId) {
            return savedStock;
        }

        @Override
        public GroupBuyStock releasePaidStock(String activityId, String goodsId, String orderId, String teamId) {
            return savedStock;
        }

        @Override
        public Optional<GroupBuyStock> queryByActivityId(String activityId) {
            return Optional.ofNullable(savedStock);
        }

        @Override
        public GroupBuyStock initStock(String activityId, String goodsId, int totalStock) {
            savedStock = stock(activityId, totalStock, totalStock, 0, 0);
            savedStock.setGoodsId(goodsId);
            return savedStock;
        }

        @Override
        public GroupBuyStock updateTotalStock(String activityId, int totalStock) {
            if (savedStock == null) {
                throw new AppException("GROUP_0016", "group stock not configured");
            }
            int locked = savedStock.getLockedStock() == null ? 0 : savedStock.getLockedStock();
            int paid = savedStock.getPaidStock() == null ? 0 : savedStock.getPaidStock();
            if (totalStock < locked + paid) {
                throw new AppException("GROUP_0017", "总库存不能小于已锁与已付之和");
            }
            savedStock.setTotalStock(totalStock);
            savedStock.setAvailableStock(totalStock - locked - paid);
            return savedStock;
        }

        @Override
        public boolean removeByActivityId(String activityId) {
            removedActivityIds.add(activityId);
            savedStock = null;
            return true;
        }
    }

    private static class FakeOrderLockRepository implements GroupBuyOrderLockRepository {
        private final int inProgressCount;

        FakeOrderLockRepository(int inProgressCount) {
            this.inProgressCount = inProgressCount;
        }

        @Override
        public Optional<GroupBuyOrderLock> queryLockByIdempotentKey(String idempotentKey) {
            return Optional.empty();
        }

        @Override
        public Optional<GroupBuyTeam> queryTeamByTeamId(String teamId) {
            return Optional.empty();
        }

        @Override
        public GroupBuyLockResult lockNewTeam(GroupBuyTeam team, GroupBuyOrderLock orderLock) {
            return new GroupBuyLockResult(orderLock, team, true);
        }

        @Override
        public GroupBuyLockResult lockExistingTeam(GroupBuyOrderLock orderLock) {
            return new GroupBuyLockResult(orderLock, null, true);
        }

        @Override
        public Optional<GroupBuyOrderLock> queryLockByOrderId(String orderId) {
            return Optional.empty();
        }

        @Override
        public GroupBuySettlementResult settlePaidOrder(String orderId) {
            return new GroupBuySettlementResult();
        }

        @Override
        public List<String> queryPaidOrderIdsByTeamId(String teamId) {
            return List.of();
        }

        @Override
        public GroupBuySettlementResult releaseLockedOrder(String orderId) {
            return new GroupBuySettlementResult();
        }

        @Override
        public GroupBuySettlementResult releasePaidOrder(String orderId) {
            return new GroupBuySettlementResult();
        }

        @Override
        public int countInProgressLocksByActivityId(String activityId) {
            return inProgressCount;
        }
    }
}
