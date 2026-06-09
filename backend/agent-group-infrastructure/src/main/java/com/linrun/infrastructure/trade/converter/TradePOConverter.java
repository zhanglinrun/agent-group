package com.linrun.infrastructure.trade.converter;

import com.linrun.domain.trade.model.entity.PayOrderEntity;
import com.linrun.domain.trade.model.entity.RefundOrderEntity;
import com.linrun.domain.trade.model.entity.TradeEventConsumeRecordEntity;
import com.linrun.domain.trade.model.entity.TradeEventOutboxEntity;
import com.linrun.domain.trade.model.entity.TradeOrderEntity;
import com.linrun.domain.trade.model.entity.TradeStatusFlowEntity;
import com.linrun.domain.trade.model.notify.NotifyTask;
import com.linrun.domain.trade.model.valobj.PayStatusEnumVO;
import com.linrun.domain.trade.model.valobj.RefundStatusEnumVO;
import com.linrun.domain.trade.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import com.linrun.infrastructure.po.NotifyTaskPO;
import com.linrun.infrastructure.po.PayOrderPO;
import com.linrun.infrastructure.po.RefundOrderPO;
import com.linrun.infrastructure.po.TradeEventConsumeRecordPO;
import com.linrun.infrastructure.po.TradeEventOutboxPO;
import com.linrun.infrastructure.po.TradeOrderPO;
import com.linrun.infrastructure.po.TradeStatusFlowPO;
import org.springframework.beans.BeanUtils;

import java.util.List;

public final class TradePOConverter {

    private TradePOConverter() {
    }

    public static TradeOrderPO toPO(TradeOrderEntity entity) {
        if (entity == null) {
            return null;
        }
        TradeOrderPO po = new TradeOrderPO();
        BeanUtils.copyProperties(entity, po, "buyType", "orderStatus");
        po.setBuyType(enumName(entity.getBuyType()));
        po.setOrderStatus(enumName(entity.getOrderStatus()));
        return po;
    }

    public static TradeOrderEntity toEntity(TradeOrderPO po) {
        if (po == null) {
            return null;
        }
        TradeOrderEntity entity = new TradeOrderEntity();
        BeanUtils.copyProperties(po, entity, "buyType", "orderStatus");
        entity.setBuyType(enumValue(TradeBuyTypeEnumVO.class, po.getBuyType()));
        entity.setOrderStatus(enumValue(TradeOrderStatusEnumVO.class, po.getOrderStatus()));
        return entity;
    }

    public static List<TradeOrderEntity> toTradeOrderEntities(List<TradeOrderPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(TradePOConverter::toEntity).toList();
    }

    public static PayOrderPO toPO(PayOrderEntity entity) {
        if (entity == null) {
            return null;
        }
        PayOrderPO po = new PayOrderPO();
        BeanUtils.copyProperties(entity, po, "payStatus");
        po.setPayStatus(enumName(entity.getPayStatus()));
        return po;
    }

    public static PayOrderEntity toEntity(PayOrderPO po) {
        if (po == null) {
            return null;
        }
        PayOrderEntity entity = new PayOrderEntity();
        BeanUtils.copyProperties(po, entity, "payStatus");
        entity.setPayStatus(enumValue(PayStatusEnumVO.class, po.getPayStatus()));
        return entity;
    }

    public static RefundOrderPO toPO(RefundOrderEntity entity) {
        if (entity == null) {
            return null;
        }
        RefundOrderPO po = new RefundOrderPO();
        BeanUtils.copyProperties(entity, po, "refundStatus");
        po.setRefundStatus(enumName(entity.getRefundStatus()));
        return po;
    }

    public static RefundOrderEntity toEntity(RefundOrderPO po) {
        if (po == null) {
            return null;
        }
        RefundOrderEntity entity = new RefundOrderEntity();
        BeanUtils.copyProperties(po, entity, "refundStatus");
        entity.setRefundStatus(enumValue(RefundStatusEnumVO.class, po.getRefundStatus()));
        return entity;
    }

    public static List<RefundOrderEntity> toRefundOrderEntities(List<RefundOrderPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(TradePOConverter::toEntity).toList();
    }

    public static TradeStatusFlowPO toPO(TradeStatusFlowEntity entity) {
        if (entity == null) {
            return null;
        }
        TradeStatusFlowPO po = new TradeStatusFlowPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static TradeStatusFlowEntity toEntity(TradeStatusFlowPO po) {
        if (po == null) {
            return null;
        }
        TradeStatusFlowEntity entity = new TradeStatusFlowEntity();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<TradeStatusFlowEntity> toTradeStatusFlowEntities(List<TradeStatusFlowPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(TradePOConverter::toEntity).toList();
    }

    public static TradeEventOutboxPO toPO(TradeEventOutboxEntity entity) {
        if (entity == null) {
            return null;
        }
        TradeEventOutboxPO po = new TradeEventOutboxPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static TradeEventOutboxEntity toEntity(TradeEventOutboxPO po) {
        if (po == null) {
            return null;
        }
        TradeEventOutboxEntity entity = new TradeEventOutboxEntity();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<TradeEventOutboxEntity> toTradeEventOutboxEntities(List<TradeEventOutboxPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(TradePOConverter::toEntity).toList();
    }

    public static TradeEventConsumeRecordPO toPO(TradeEventConsumeRecordEntity entity) {
        if (entity == null) {
            return null;
        }
        TradeEventConsumeRecordPO po = new TradeEventConsumeRecordPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static TradeEventConsumeRecordEntity toEntity(TradeEventConsumeRecordPO po) {
        if (po == null) {
            return null;
        }
        TradeEventConsumeRecordEntity entity = new TradeEventConsumeRecordEntity();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static NotifyTaskPO toPO(NotifyTask entity) {
        if (entity == null) {
            return null;
        }
        NotifyTaskPO po = new NotifyTaskPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static NotifyTask toEntity(NotifyTaskPO po) {
        if (po == null) {
            return null;
        }
        NotifyTask entity = new NotifyTask();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<NotifyTask> toNotifyTasks(List<NotifyTaskPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(TradePOConverter::toEntity).toList();
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> enumType, String value) {
        return value == null ? null : Enum.valueOf(enumType, value);
    }
}















