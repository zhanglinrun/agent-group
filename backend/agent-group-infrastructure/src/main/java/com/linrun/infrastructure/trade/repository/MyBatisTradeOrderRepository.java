package com.linrun.infrastructure.trade.repository;

import com.linrun.domain.trade.adapter.TradeOrderRepository;
import com.linrun.domain.trade.model.PayOrder;
import com.linrun.domain.trade.model.TradeOrder;
import com.linrun.infrastructure.dao.ITradeOrderDao;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
    public Optional<TradeOrder> queryTradeOrderByOrderId(String orderId) {
        return Optional.ofNullable(tradeOrderDao.queryTradeOrderByOrderId(orderId));
    }

    @Override
    public Optional<PayOrder> queryPayOrderByOrderId(String orderId) {
        return Optional.ofNullable(tradeOrderDao.queryPayOrderByOrderId(orderId));
    }
}
