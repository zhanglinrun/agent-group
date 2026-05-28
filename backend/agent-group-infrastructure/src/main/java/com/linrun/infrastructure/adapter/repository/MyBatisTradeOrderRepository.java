package com.linrun.infrastructure.adapter.repository;

import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.infrastructure.converter.TradePOConverter;
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
        tradeOrderDao.insertTradeOrder(TradePOConverter.toPO(tradeOrder));
        tradeOrderDao.insertPayOrder(TradePOConverter.toPO(payOrder));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePaySuccess(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        tradeOrderDao.updateTradeOrderPaySuccess(TradePOConverter.toPO(tradeOrder));
        tradeOrderDao.updatePayOrderSuccess(TradePOConverter.toPO(payOrder));
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
        tradeOrderDao.updateTradeOrderClosed(TradePOConverter.toPO(tradeOrder));
        tradeOrderDao.updatePayOrderClosed(TradePOConverter.toPO(payOrder));
    }

    @Override
    public void saveRefundOrder(RefundOrderEntity refundOrder) {
        tradeOrderDao.insertRefundOrder(TradePOConverter.toPO(refundOrder));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRefunded(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        tradeOrderDao.updateTradeOrderRefunded(TradePOConverter.toPO(tradeOrder));
        tradeOrderDao.updatePayOrderRefunded(TradePOConverter.toPO(payOrder));
    }

    @Override
    public void updateDealDone(TradeOrderEntity tradeOrder) {
        tradeOrderDao.updateTradeOrderDealDone(TradePOConverter.toPO(tradeOrder));
    }

    @Override
    public Optional<RefundOrderEntity> queryRefundOrderByOrderId(String orderId) {
        return Optional.ofNullable(TradePOConverter.toEntity(tradeOrderDao.queryRefundOrderByOrderId(orderId)));
    }

    @Override
    public Optional<TradeOrderEntity> queryTradeOrderByOrderId(String orderId) {
        return Optional.ofNullable(TradePOConverter.toEntity(tradeOrderDao.queryTradeOrderByOrderId(orderId)));
    }

    @Override
    public Optional<TradeOrderEntity> queryTradeOrderByIdempotentKey(String idempotentKey) {
        return Optional.ofNullable(TradePOConverter.toEntity(tradeOrderDao.queryTradeOrderByIdempotentKey(idempotentKey)));
    }

    @Override
    public Optional<PayOrderEntity> queryPayOrderByOrderId(String orderId) {
        return Optional.ofNullable(TradePOConverter.toEntity(tradeOrderDao.queryPayOrderByOrderId(orderId)));
    }

    @Override
    public List<TradeOrderEntity> queryUserTradeOrders(String userId, Long lastId, int pageSize) {
        return TradePOConverter.toTradeOrderEntities(tradeOrderDao.queryUserTradeOrders(userId, lastId, pageSize));
    }

    @Override
    public List<String> queryTimeoutPayWaitOrderIds(LocalDateTime deadline, int limit) {
        return tradeOrderDao.queryTimeoutPayWaitOrderIds(deadline, limit);
    }

    @Override
    public Optional<TradeOrderEntity> queryLatestUnpaidOrder(String userId, String goodsId, TradeBuyTypeEnumVO buyType) {
        return Optional.ofNullable(TradePOConverter.toEntity(
                tradeOrderDao.queryLatestUnpaidOrder(userId, goodsId, buyType == null ? null : buyType.name())));
    }
}
