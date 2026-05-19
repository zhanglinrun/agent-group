package com.linrun.infrastructure.trade.repository;

import com.linrun.domain.trade.adapter.TradeOrderRepository;
import com.linrun.domain.trade.model.PayOrder;
import com.linrun.domain.trade.model.RefundOrder;
import com.linrun.domain.trade.model.TradeBuyType;
import com.linrun.domain.trade.model.TradeOrder;
import com.linrun.infrastructure.dao.ITradeOrderDao;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisTradeOrderRepository implements TradeOrderRepository {

    private final ITradeOrderDao tradeOrderDao;

    public MyBatisTradeOrderRepository(ITradeOrderDao tradeOrderDao) {
        this.tradeOrderDao = tradeOrderDao;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void save(TradeOrder tradeOrder, PayOrder payOrder) {
        tradeOrderDao.insertTradeOrder(tradeOrder);
        tradeOrderDao.insertPayOrder(payOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePaySuccess(TradeOrder tradeOrder, PayOrder payOrder) {
        tradeOrderDao.updateTradeOrderPaySuccess(tradeOrder);
        tradeOrderDao.updatePayOrderSuccess(payOrder);
    }

    @Override
    public void updateGroupSettledByOrderIds(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        tradeOrderDao.updateGroupSettledByOrderIds(orderIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCloseUnpaid(TradeOrder tradeOrder, PayOrder payOrder) {
        tradeOrderDao.updateTradeOrderClosed(tradeOrder);
        tradeOrderDao.updatePayOrderClosed(payOrder);
    }

    @Override
    public void saveRefundOrder(RefundOrder refundOrder) {
        tradeOrderDao.insertRefundOrder(refundOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRefunded(TradeOrder tradeOrder, PayOrder payOrder) {
        tradeOrderDao.updateTradeOrderRefunded(tradeOrder);
        tradeOrderDao.updatePayOrderRefunded(payOrder);
    }

    @Override
    public void updateDealDone(TradeOrder tradeOrder) {
        tradeOrderDao.updateTradeOrderDealDone(tradeOrder);
    }

    @Override
    public Optional<RefundOrder> queryRefundOrderByOrderId(String orderId) {
        return Optional.ofNullable(tradeOrderDao.queryRefundOrderByOrderId(orderId));
    }

    @Override
    public Optional<TradeOrder> queryTradeOrderByOrderId(String orderId) {
        return Optional.ofNullable(tradeOrderDao.queryTradeOrderByOrderId(orderId));
    }

    @Override
    public Optional<PayOrder> queryPayOrderByOrderId(String orderId) {
        return Optional.ofNullable(tradeOrderDao.queryPayOrderByOrderId(orderId));
    }

    @Override
    public List<TradeOrder> queryUserTradeOrders(String userId, Long lastId, int pageSize) {
        return tradeOrderDao.queryUserTradeOrders(userId, lastId, pageSize);
    }

    @Override
    public List<String> queryTimeoutPayWaitOrderIds(LocalDateTime deadline, int limit) {
        return tradeOrderDao.queryTimeoutPayWaitOrderIds(deadline, limit);
    }

    @Override
    public Optional<TradeOrder> queryLatestUnpaidOrder(String userId, String goodsId, TradeBuyType buyType) {
        return Optional.ofNullable(tradeOrderDao.queryLatestUnpaidOrder(userId, goodsId, buyType));
    }
}
