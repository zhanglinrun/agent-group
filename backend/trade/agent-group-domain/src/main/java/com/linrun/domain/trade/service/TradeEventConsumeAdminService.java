package com.linrun.domain.trade.service;

import com.linrun.domain.trade.adapter.repository.TradeEventConsumeRecordRepository;
import com.linrun.domain.trade.adapter.repository.TradeEventPublisher;
import com.linrun.domain.trade.model.entity.TradeEventConsumeRecordEntity;
import com.linrun.domain.trade.model.entity.TradeEventMessageEntity;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 交易事件消费记录管理：死信查询与人工重投。
 */
@Service
public class TradeEventConsumeAdminService {

    private final TradeEventConsumeRecordRepository consumeRecordRepository;
    private final TradeEventPublisher tradeEventPublisher;

    public TradeEventConsumeAdminService(TradeEventConsumeRecordRepository consumeRecordRepository,
                                         TradeEventPublisher tradeEventPublisher) {
        this.consumeRecordRepository = consumeRecordRepository;
        this.tradeEventPublisher = tradeEventPublisher == null ? TradeEventPublisher.noop() : tradeEventPublisher;
    }

    public List<TradeEventConsumeRecordEntity> listDeadLetters(int limit) {
        return consumeRecordRepository.queryByStatus(TradeEventConsumeRecordEntity.STATUS_DEAD_LETTER, limit);
    }

    public boolean replayDeadLetter(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            throw new AppException("TRADE_EVENT_0001", "eventId 不能为空");
        }
        TradeEventConsumeRecordEntity record = consumeRecordRepository.queryByEventId(eventId.trim())
                .orElseThrow(() -> new AppException("TRADE_EVENT_0002", "消费记录不存在"));
        if (record.getConsumeStatus() == null
                || record.getConsumeStatus() != TradeEventConsumeRecordEntity.STATUS_DEAD_LETTER) {
            throw new AppException("TRADE_EVENT_0003", "仅允许重投死信状态的事件");
        }
        if (consumeRecordRepository.resetStatusForReplay(eventId.trim()) != 1) {
            throw new AppException("TRADE_EVENT_0004", "重投状态重置失败，请刷新后重试");
        }
        tradeEventPublisher.publish(toMessage(record));
        return true;
    }

    private TradeEventMessageEntity toMessage(TradeEventConsumeRecordEntity record) {
        TradeEventMessageEntity message = new TradeEventMessageEntity();
        message.setFlowId(record.getEventId());
        message.setOrderId(record.getOrderId());
        message.setBizType(record.getBizType());
        message.setBizId(record.getBizId());
        message.setEventType(record.getEventType());
        message.setRoutingKey(record.getRoutingKey());
        message.setRemark("admin replay");
        message.setCreateTime(record.getCreateTime());
        return message;
    }
}
