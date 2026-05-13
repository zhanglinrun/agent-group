package com.linrun.trigger.service;

import com.linrun.api.groupbuy.request.LockGroupBuyOrderRequest;
import com.linrun.api.groupbuy.request.CloseUnpaidGroupBuyOrderRequest;
import com.linrun.api.groupbuy.request.RefundGroupBuyOrderRequest;
import com.linrun.api.groupbuy.response.GroupBuyCompensationResponse;
import com.linrun.api.groupbuy.response.LockGroupBuyOrderResponse;
import com.linrun.api.trade.request.MockPayCallbackRequest;
import com.linrun.api.trade.response.MockPayCallbackResponse;
import com.linrun.domain.groupbuy.adapter.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.adapter.GroupBuyOrderLockRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.model.GroupBuyLockResult;
import com.linrun.domain.groupbuy.model.GroupBuyLockStatus;
import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.groupbuy.model.GroupBuySettlementResult;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import com.linrun.domain.groupbuy.model.GroupBuyTeamStatus;
import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.domain.trade.adapter.TradeOrderRepository;
import com.linrun.domain.trade.model.PayOrder;
import com.linrun.domain.trade.model.PayStatus;
import com.linrun.domain.trade.model.RefundOrder;
import com.linrun.domain.trade.model.TradeBuyType;
import com.linrun.domain.trade.model.TradeOrder;
import com.linrun.domain.trade.model.TradeOrderStatus;
import com.linrun.domain.trade.service.TradeOrderService;
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
        FakeTradeOrderRepository tradeOrderRepository = new FakeTradeOrderRepository();
        GroupBuyLockOrderService service = service(lockRepository, tradeOrderRepository);

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
        assertTrue(response.getOrderId().startsWith("O"));
        assertTrue(response.getPayOrderId().startsWith("P"));
        assertEquals(TradeOrderStatus.PAY_WAIT.name(), response.getOrderStatus());
        assertEquals(PayStatus.WAIT_PAY.name(), response.getPayStatus());
        assertTrue(response.getPayUrl().contains(response.getOrderId()));
        assertEquals(TradeBuyType.GROUP_BUY, tradeOrderRepository.savedTradeOrder.getBuyType());
        assertEquals("A10001", tradeOrderRepository.savedTradeOrder.getActivityId());
        assertEquals(new BigDecimal("2399.00"), tradeOrderRepository.savedTradeOrder.getOriginAmount());
        assertEquals(new BigDecimal("2099.00"), tradeOrderRepository.savedTradeOrder.getPayAmount());
        assertEquals(response.getOrderId(), lockRepository.locks.get("IDEM_10001").getOrderId());
    }

    @Test
    void shouldJoinExistingTeamAndIncreaseLockCount() {
        FakeGroupBuyOrderLockRepository lockRepository = new FakeGroupBuyOrderLockRepository();
        GroupBuyTeam team = team("T10001", 1);
        lockRepository.teams.put(team.getTeamId(), team);
        GroupBuyLockOrderService service = service(lockRepository, new FakeTradeOrderRepository());

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
        GroupBuyLockOrderService service = service(lockRepository, new FakeTradeOrderRepository());

        AppException exception = assertThrows(AppException.class,
                () -> service.lock(request("T10001", "IDEM_10003")));

        assertEquals("GROUP_0007", exception.getCode());
        assertEquals("拼团队伍名额已满", exception.getMessage());
    }

    @Test
    void shouldReturnExistingLockWhenIdempotentKeyRepeated() {
        FakeGroupBuyOrderLockRepository lockRepository = new FakeGroupBuyOrderLockRepository();
        GroupBuyTeam team = team("T10001", 1);
        lockRepository.teams.put(team.getTeamId(), team);
        FakeTradeOrderRepository tradeOrderRepository = new FakeTradeOrderRepository();
        GroupBuyLockOrderService service = service(lockRepository, tradeOrderRepository);

        LockGroupBuyOrderResponse firstResponse = service.lock(request("T10001", "IDEM_10004"));
        GroupBuyOrderLock orderLock = lockRepository.locks.get("IDEM_10004");

        LockGroupBuyOrderResponse response = service.lock(request("T10001", "IDEM_10004"));

        assertTrue(response.isRepeated());
        assertEquals(firstResponse.getLockId(), response.getLockId());
        assertEquals(firstResponse.getOrderId(), response.getOrderId());
        assertEquals(firstResponse.getPayOrderId(), response.getPayOrderId());
        assertEquals(orderLock.getOrderId(), response.getOrderId());
        assertEquals(2, response.getLockedCount());
        assertEquals(2, lockRepository.teams.get("T10001").getLockCount());
    }

    @Test
    void shouldMarkGroupBuyOrderPaySuccessAfterMockCallback() {
        FakeGroupBuyOrderLockRepository lockRepository = new FakeGroupBuyOrderLockRepository();
        FakeTradeOrderRepository tradeOrderRepository = new FakeTradeOrderRepository();
        GroupBuyLockOrderService lockOrderService = service(lockRepository, tradeOrderRepository);
        LockGroupBuyOrderResponse lockResponse = lockOrderService.lock(request(null, "IDEM_10006"));
        MockPayCallbackService callbackService = callbackService(lockRepository, tradeOrderRepository);
        MockPayCallbackRequest callbackRequest = new MockPayCallbackRequest();
        callbackRequest.setOrderId(lockResponse.getOrderId());
        callbackRequest.setOutTradeNo("T10006");

        MockPayCallbackResponse callbackResponse = callbackService.paySuccess(callbackRequest);

        assertEquals(lockResponse.getOrderId(), callbackResponse.getOrderId());
        assertEquals(lockResponse.getPayOrderId(), callbackResponse.getPayOrderId());
        assertEquals(TradeOrderStatus.PAY_SUCCESS.name(), callbackResponse.getOrderStatus());
        assertEquals(PayStatus.SUCCESS.name(), callbackResponse.getPayStatus());
        assertEquals(TradeOrderStatus.PAY_SUCCESS, tradeOrderRepository.savedTradeOrder.getOrderStatus());
        assertEquals(PayStatus.SUCCESS, tradeOrderRepository.savedPayOrder.getPayStatus());
        assertEquals(GroupBuyLockStatus.PAID, lockRepository.locks.get("IDEM_10006").getLockStatus());
        assertEquals(1, lockRepository.teams.get(lockResponse.getTeamId()).getCompleteCount());
    }

    @Test
    void shouldSettleTeamWhenPaidCountReachesTarget() {
        FakeGroupBuyOrderLockRepository lockRepository = new FakeGroupBuyOrderLockRepository();
        FakeTradeOrderRepository tradeOrderRepository = new FakeTradeOrderRepository();
        GroupBuyLockOrderService lockOrderService = service(lockRepository, tradeOrderRepository);
        MockPayCallbackService callbackService = callbackService(lockRepository, tradeOrderRepository);

        LockGroupBuyOrderResponse first = lockOrderService.lock(request(null, "IDEM_20001"));
        LockGroupBuyOrderResponse second = lockOrderService.lock(request(first.getTeamId(), "IDEM_20002"));
        LockGroupBuyOrderResponse third = lockOrderService.lock(request(first.getTeamId(), "IDEM_20003"));

        callbackService.paySuccess(callback(first.getOrderId(), "T20001"));
        callbackService.paySuccess(callback(second.getOrderId(), "T20002"));
        MockPayCallbackResponse thirdCallback = callbackService.paySuccess(callback(third.getOrderId(), "T20003"));

        assertEquals(TradeOrderStatus.GROUP_SETTLED.name(), thirdCallback.getOrderStatus());
        assertEquals(GroupBuyTeamStatus.SUCCESS, lockRepository.teams.get(first.getTeamId()).getTeamStatus());
        assertEquals(3, lockRepository.teams.get(first.getTeamId()).getCompleteCount());
        assertEquals(TradeOrderStatus.GROUP_SETTLED, tradeOrderRepository.tradeOrders.get(first.getOrderId()).getOrderStatus());
        assertEquals(TradeOrderStatus.GROUP_SETTLED, tradeOrderRepository.tradeOrders.get(second.getOrderId()).getOrderStatus());
        assertEquals(TradeOrderStatus.GROUP_SETTLED, tradeOrderRepository.tradeOrders.get(third.getOrderId()).getOrderStatus());
    }

    @Test
    void shouldCloseUnpaidOrderAndReleaseLockSlot() {
        FakeGroupBuyOrderLockRepository lockRepository = new FakeGroupBuyOrderLockRepository();
        FakeTradeOrderRepository tradeOrderRepository = new FakeTradeOrderRepository();
        GroupBuyLockOrderService lockOrderService = service(lockRepository, tradeOrderRepository);
        GroupBuyCompensationService compensationService = compensationService(lockRepository, tradeOrderRepository);
        LockGroupBuyOrderResponse lockResponse = lockOrderService.lock(request(null, "IDEM_30001"));
        CloseUnpaidGroupBuyOrderRequest closeRequest = new CloseUnpaidGroupBuyOrderRequest();
        closeRequest.setOrderId(lockResponse.getOrderId());

        GroupBuyCompensationResponse response = compensationService.closeUnpaid(closeRequest);

        assertEquals(lockResponse.getOrderId(), response.getOrderId());
        assertEquals(TradeOrderStatus.CLOSED.name(), response.getOrderStatus());
        assertEquals(PayStatus.CLOSED.name(), response.getPayStatus());
        assertEquals(GroupBuyLockStatus.RELEASED.name(), response.getLockStatus());
        assertEquals(0, response.getLockedCount());
        assertEquals(0, response.getCompleteCount());
        assertEquals(TradeOrderStatus.CLOSED, tradeOrderRepository.tradeOrders.get(lockResponse.getOrderId()).getOrderStatus());
        assertEquals(PayStatus.CLOSED, tradeOrderRepository.payOrders.get(lockResponse.getOrderId()).getPayStatus());
    }

    @Test
    void shouldRefundPaidUnsettledOrderAndCreateRefundRecord() {
        FakeGroupBuyOrderLockRepository lockRepository = new FakeGroupBuyOrderLockRepository();
        FakeTradeOrderRepository tradeOrderRepository = new FakeTradeOrderRepository();
        GroupBuyLockOrderService lockOrderService = service(lockRepository, tradeOrderRepository);
        MockPayCallbackService callbackService = callbackService(lockRepository, tradeOrderRepository);
        GroupBuyCompensationService compensationService = compensationService(lockRepository, tradeOrderRepository);
        LockGroupBuyOrderResponse lockResponse = lockOrderService.lock(request(null, "IDEM_30002"));
        callbackService.paySuccess(callback(lockResponse.getOrderId(), "T30002"));
        RefundGroupBuyOrderRequest refundRequest = new RefundGroupBuyOrderRequest();
        refundRequest.setOrderId(lockResponse.getOrderId());
        refundRequest.setRefundReason("拼团超时未成团");

        GroupBuyCompensationResponse response = compensationService.refundUnsettled(refundRequest);

        assertTrue(response.getRefundId().startsWith("R"));
        assertEquals(TradeOrderStatus.REFUNDED.name(), response.getOrderStatus());
        assertEquals(PayStatus.REFUNDED.name(), response.getPayStatus());
        assertEquals(GroupBuyLockStatus.RELEASED.name(), response.getLockStatus());
        assertEquals(new BigDecimal("2099.00"), response.getRefundAmount());
        assertEquals(0, response.getLockedCount());
        assertEquals(0, response.getCompleteCount());
        assertEquals(TradeOrderStatus.REFUNDED, tradeOrderRepository.tradeOrders.get(lockResponse.getOrderId()).getOrderStatus());
        assertEquals(PayStatus.REFUNDED, tradeOrderRepository.payOrders.get(lockResponse.getOrderId()).getPayStatus());
        assertEquals("拼团超时未成团", tradeOrderRepository.refundOrders.get(lockResponse.getOrderId()).getRefundReason());
    }

    @Test
    void shouldRejectEndedActivity() {
        GroupBuyLockOrderService service = new GroupBuyLockOrderService(
                new FakeGuideDataRepository(),
                new FakeGroupBuyActivityRepository(activity("A10001", "G10001", LocalDateTime.now().minusHours(1))),
                new FakeGroupBuyOrderLockRepository(),
                new FakeTradeOrderRepository(),
                new TradeOrderService());

        AppException exception = assertThrows(AppException.class,
                () -> service.lock(request(null, "IDEM_10005")));

        assertEquals("GROUP_0008", exception.getCode());
        assertEquals("拼团活动不可用", exception.getMessage());
    }

    private GroupBuyLockOrderService service(FakeGroupBuyOrderLockRepository lockRepository,
                                             FakeTradeOrderRepository tradeOrderRepository) {
        return new GroupBuyLockOrderService(
                new FakeGuideDataRepository(),
                new FakeGroupBuyActivityRepository(activity("A10001", "G10001", END_TIME)),
                lockRepository,
                tradeOrderRepository,
                new TradeOrderService());
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

    private MockPayCallbackRequest callback(String orderId, String outTradeNo) {
        MockPayCallbackRequest callback = new MockPayCallbackRequest();
        callback.setOrderId(orderId);
        callback.setOutTradeNo(outTradeNo);
        return callback;
    }

    private MockPayCallbackService callbackService(FakeGroupBuyOrderLockRepository lockRepository,
                                                   FakeTradeOrderRepository tradeOrderRepository) {
        return new MockPayCallbackService(
                tradeOrderRepository,
                new TradeOrderService(),
                new GroupBuySettlementService(lockRepository, tradeOrderRepository));
    }

    private GroupBuyCompensationService compensationService(FakeGroupBuyOrderLockRepository lockRepository,
                                                            FakeTradeOrderRepository tradeOrderRepository) {
        return new GroupBuyCompensationService(
                tradeOrderRepository,
                new TradeOrderService(),
                lockRepository);
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

        @Override
        public Optional<GroupBuyOrderLock> queryLockByOrderId(String orderId) {
            return locks.values().stream()
                    .filter(orderLock -> orderLock.getOrderId().equals(orderId))
                    .findFirst();
        }

        @Override
        public GroupBuySettlementResult settlePaidOrder(String orderId) {
            GroupBuyOrderLock orderLock = queryLockByOrderId(orderId)
                    .orElseThrow(() -> new AppException("GROUP_0011", "拼团锁单不存在"));
            boolean repeated = GroupBuyLockStatus.PAID.equals(orderLock.getLockStatus());
            if (!repeated) {
                orderLock.setLockStatus(GroupBuyLockStatus.PAID);
                GroupBuyTeam team = teams.get(orderLock.getTeamId());
                team.setCompleteCount(team.getCompleteCount() + 1);
                if (team.getCompleteCount() >= team.getTargetCount()) {
                    team.setTeamStatus(GroupBuyTeamStatus.SUCCESS);
                }
            }
            return new GroupBuySettlementResult(orderLock, teams.get(orderLock.getTeamId()), repeated);
        }

        @Override
        public List<String> queryPaidOrderIdsByTeamId(String teamId) {
            return locks.values().stream()
                    .filter(orderLock -> teamId.equals(orderLock.getTeamId()))
                    .filter(orderLock -> GroupBuyLockStatus.PAID.equals(orderLock.getLockStatus()))
                    .map(GroupBuyOrderLock::getOrderId)
                    .toList();
        }

        @Override
        public GroupBuySettlementResult releaseLockedOrder(String orderId) {
            GroupBuyOrderLock orderLock = queryLockByOrderId(orderId)
                    .orElseThrow(() -> new AppException("GROUP_0011", "拼团锁单不存在"));
            boolean repeated = GroupBuyLockStatus.RELEASED.equals(orderLock.getLockStatus());
            if (!repeated && GroupBuyLockStatus.LOCKED.equals(orderLock.getLockStatus())) {
                orderLock.setLockStatus(GroupBuyLockStatus.RELEASED);
                GroupBuyTeam team = teams.get(orderLock.getTeamId());
                team.setLockCount(Math.max(team.getLockCount() - 1, 0));
            }
            return new GroupBuySettlementResult(orderLock, teams.get(orderLock.getTeamId()), repeated);
        }

        @Override
        public GroupBuySettlementResult releasePaidOrder(String orderId) {
            GroupBuyOrderLock orderLock = queryLockByOrderId(orderId)
                    .orElseThrow(() -> new AppException("GROUP_0011", "拼团锁单不存在"));
            boolean repeated = GroupBuyLockStatus.RELEASED.equals(orderLock.getLockStatus());
            if (!repeated && GroupBuyLockStatus.PAID.equals(orderLock.getLockStatus())) {
                orderLock.setLockStatus(GroupBuyLockStatus.RELEASED);
                GroupBuyTeam team = teams.get(orderLock.getTeamId());
                team.setLockCount(Math.max(team.getLockCount() - 1, 0));
                team.setCompleteCount(Math.max(team.getCompleteCount() - 1, 0));
            }
            return new GroupBuySettlementResult(orderLock, teams.get(orderLock.getTeamId()), repeated);
        }
    }

    private static class FakeTradeOrderRepository implements TradeOrderRepository {

        private TradeOrder savedTradeOrder;
        private PayOrder savedPayOrder;
        private final Map<String, TradeOrder> tradeOrders = new HashMap<>();
        private final Map<String, PayOrder> payOrders = new HashMap<>();
        private final Map<String, RefundOrder> refundOrders = new HashMap<>();

        @Override
        public void save(TradeOrder tradeOrder, PayOrder payOrder) {
            this.savedTradeOrder = tradeOrder;
            this.savedPayOrder = payOrder;
            this.tradeOrders.put(tradeOrder.getOrderId(), tradeOrder);
            this.payOrders.put(payOrder.getOrderId(), payOrder);
        }

        @Override
        public void updatePaySuccess(TradeOrder tradeOrder, PayOrder payOrder) {
            this.savedTradeOrder = tradeOrder;
            this.savedPayOrder = payOrder;
            this.tradeOrders.put(tradeOrder.getOrderId(), tradeOrder);
            this.payOrders.put(payOrder.getOrderId(), payOrder);
        }

        @Override
        public void updateGroupSettledByOrderIds(List<String> orderIds) {
            orderIds.stream()
                    .map(tradeOrders::get)
                    .filter(tradeOrder -> tradeOrder != null && TradeOrderStatus.PAY_SUCCESS.equals(tradeOrder.getOrderStatus()))
                    .forEach(TradeOrder::markGroupSettled);
        }

        @Override
        public void updateCloseUnpaid(TradeOrder tradeOrder, PayOrder payOrder) {
            this.savedTradeOrder = tradeOrder;
            this.savedPayOrder = payOrder;
            this.tradeOrders.put(tradeOrder.getOrderId(), tradeOrder);
            this.payOrders.put(payOrder.getOrderId(), payOrder);
        }

        @Override
        public void saveRefundOrder(RefundOrder refundOrder) {
            this.refundOrders.put(refundOrder.getOrderId(), refundOrder);
        }

        @Override
        public void updateRefunded(TradeOrder tradeOrder, PayOrder payOrder) {
            this.savedTradeOrder = tradeOrder;
            this.savedPayOrder = payOrder;
            this.tradeOrders.put(tradeOrder.getOrderId(), tradeOrder);
            this.payOrders.put(payOrder.getOrderId(), payOrder);
        }

        @Override
        public Optional<RefundOrder> queryRefundOrderByOrderId(String orderId) {
            return Optional.ofNullable(refundOrders.get(orderId));
        }

        @Override
        public Optional<TradeOrder> queryTradeOrderByOrderId(String orderId) {
            return Optional.ofNullable(tradeOrders.get(orderId));
        }

        @Override
        public Optional<PayOrder> queryPayOrderByOrderId(String orderId) {
            return Optional.ofNullable(payOrders.get(orderId));
        }
    }
}
