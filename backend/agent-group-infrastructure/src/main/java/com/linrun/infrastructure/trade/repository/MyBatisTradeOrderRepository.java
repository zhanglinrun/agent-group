package com.linrun.infrastructure.trade.repository;

import com.linrun.domain.trade.adapter.repository.TradeOrderRepository;
import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.infrastructure.trade.converter.TradePOConverter;
import com.linrun.infrastructure.dao.ITradeOrderDao;
import com.linrun.types.exception.AppException;
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
        assertUpdated(tradeOrderDao.updateTradeOrderPaySuccess(TradePOConverter.toPO(tradeOrder)),
                "TRADE_0020", "order status changed before pay success update");
        assertUpdated(tradeOrderDao.updatePayOrderSuccess(TradePOConverter.toPO(payOrder)),
                "TRADE_0020", "pay order status changed before pay success update");
    }

    @Override
    public void updatePaymentGatewayInfo(PayOrderEntity payOrder) {
        assertUpdated(tradeOrderDao.updatePayOrderGatewayInfo(TradePOConverter.toPO(payOrder)),
                "PAY_0017", "pay order status changed before gateway payment update");
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
        assertUpdated(tradeOrderDao.updateTradeOrderClosed(TradePOConverter.toPO(tradeOrder)),
                "TRADE_0021", "order status changed before close update");
        assertUpdated(tradeOrderDao.updatePayOrderClosed(TradePOConverter.toPO(payOrder)),
                "TRADE_0021", "pay order status changed before close update");
    }

    @Override
    public void saveRefundOrder(RefundOrderEntity refundOrder) {
        tradeOrderDao.insertRefundOrder(TradePOConverter.toPO(refundOrder));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRefunded(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        assertUpdated(tradeOrderDao.updateTradeOrderRefunded(TradePOConverter.toPO(tradeOrder)),
                "TRADE_0022", "order status changed before refund update");
        assertUpdated(tradeOrderDao.updatePayOrderRefunded(TradePOConverter.toPO(payOrder)),
                "TRADE_0022", "pay order status changed before refund update");
    }

    @Override
    public void updateDealDone(TradeOrderEntity tradeOrder) {
        assertUpdated(tradeOrderDao.updateTradeOrderDealDone(TradePOConverter.toPO(tradeOrder)),
                "TRADE_0023", "order status changed before deal done update");
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
    public List<TradeOrderEntity> queryUserTradeOrders(String userId,
                                                       Long lastId,
                                                       int pageSize,
                                                       Integer marketType,
                                                       String orderStatus,
                                                       String keyword) {
        String buyType = marketType == null ? null : (marketType == 1 ? TradeBuyTypeEnumVO.GROUP_BUY.name() : TradeBuyTypeEnumVO.DIRECT.name());
        return TradePOConverter.toTradeOrderEntities(
                tradeOrderDao.queryUserTradeOrdersFiltered(userId, lastId, pageSize, buyType, orderStatus, keyword));
    }

    @Override
    public List<TradeOrderEntity> queryTradeOrders(Long lastId,
                                                   int pageSize,
                                                   Integer marketType,
                                                   String orderStatus,
                                                   String keyword) {
        String buyType = marketType == null ? null : (marketType == 1 ? TradeBuyTypeEnumVO.GROUP_BUY.name() : TradeBuyTypeEnumVO.DIRECT.name());
        return TradePOConverter.toTradeOrderEntities(
                tradeOrderDao.queryTradeOrdersFiltered(lastId, Math.max(1, pageSize), buyType, orderStatus, keyword));
    }

    @Override
    public List<RefundOrderEntity> queryRefundOrders(String userId, String refundStatus, int pageSize) {
        return TradePOConverter.toRefundOrderEntities(
                tradeOrderDao.queryRefundOrders(userId, refundStatus, Math.max(1, pageSize)));
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

    private void assertUpdated(int affected, String code, String message) {
        if (affected <= 0) {
            throw new AppException(code, message);
        }
    }
}















