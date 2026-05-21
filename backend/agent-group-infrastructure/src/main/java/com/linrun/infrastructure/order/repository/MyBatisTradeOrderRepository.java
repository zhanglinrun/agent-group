package com.linrun.infrastructure.order.repository;

import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.entity.RefundOrderEntity;
import com.linrun.domain.order.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
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
    public void save(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        tradeOrderDao.insertTradeOrder(tradeOrder);
        tradeOrderDao.insertPayOrder(payOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePaySuccess(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
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
    public void updateCloseUnpaid(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        tradeOrderDao.updateTradeOrderClosed(tradeOrder);
        tradeOrderDao.updatePayOrderClosed(payOrder);
    }

    @Override
    public void saveRefundOrder(RefundOrderEntity refundOrder) {
        tradeOrderDao.insertRefundOrder(refundOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRefunded(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        tradeOrderDao.updateTradeOrderRefunded(tradeOrder);
        tradeOrderDao.updatePayOrderRefunded(payOrder);
    }

    @Override
    public void updateDealDone(TradeOrderEntity tradeOrder) {
        tradeOrderDao.updateTradeOrderDealDone(tradeOrder);
    }

    @Override
    public Optional<RefundOrderEntity> queryRefundOrderByOrderId(String orderId) {
        return Optional.ofNullable(tradeOrderDao.queryRefundOrderByOrderId(orderId));
    }

    @Override
    public Optional<TradeOrderEntity> queryTradeOrderByOrderId(String orderId) {
        return Optional.ofNullable(tradeOrderDao.queryTradeOrderByOrderId(orderId));
    }

    @Override
    public Optional<PayOrderEntity> queryPayOrderByOrderId(String orderId) {
        return Optional.ofNullable(tradeOrderDao.queryPayOrderByOrderId(orderId));
    }

    @Override
    public List<TradeOrderEntity> queryUserTradeOrders(String userId, Long lastId, int pageSize) {
        return tradeOrderDao.queryUserTradeOrders(userId, lastId, pageSize);
    }

    @Override
    public List<String> queryTimeoutPayWaitOrderIds(LocalDateTime deadline, int limit) {
        return tradeOrderDao.queryTimeoutPayWaitOrderIds(deadline, limit);
    }

    @Override
    public Optional<TradeOrderEntity> queryLatestUnpaidOrder(String userId, String goodsId, TradeBuyTypeEnumVO buyType) {
        return Optional.ofNullable(tradeOrderDao.queryLatestUnpaidOrder(userId, goodsId, buyType));
    }
}
