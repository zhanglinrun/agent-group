package com.linrun.infrastructure.groupbuy.converter;

import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.model.GroupBuyDiscount;
import com.linrun.domain.groupbuy.model.GroupBuyLockStatus;
import com.linrun.domain.groupbuy.model.GroupBuyMarketSku;
import com.linrun.domain.groupbuy.model.GroupBuyOrderLock;
import com.linrun.domain.groupbuy.model.GroupBuyStock;
import com.linrun.domain.groupbuy.model.GroupBuyStockFlow;
import com.linrun.domain.groupbuy.model.GroupBuyStockFlowType;
import com.linrun.domain.groupbuy.model.GroupBuyTeam;
import com.linrun.domain.groupbuy.model.GroupBuyTeamDetail;
import com.linrun.domain.groupbuy.model.GroupBuyTeamStatistic;
import com.linrun.domain.groupbuy.model.GroupBuyTeamStatus;
import com.linrun.domain.groupbuy.model.SourceChannelSkuActivity;
import com.linrun.infrastructure.po.GroupBuyActivityPO;
import com.linrun.infrastructure.po.GroupBuyDiscountPO;
import com.linrun.infrastructure.po.GroupBuyMarketSkuPO;
import com.linrun.infrastructure.po.GroupBuyOrderLockPO;
import com.linrun.infrastructure.po.GroupBuyStockFlowPO;
import com.linrun.infrastructure.po.GroupBuyStockPO;
import com.linrun.infrastructure.po.GroupBuyTeamDetailPO;
import com.linrun.infrastructure.po.GroupBuyTeamPO;
import com.linrun.infrastructure.po.GroupBuyTeamStatisticPO;
import com.linrun.infrastructure.po.SourceChannelSkuActivityPO;
import org.springframework.beans.BeanUtils;

import java.util.List;

public final class ActivityPOConverter {

    private ActivityPOConverter() {
    }

    public static GroupBuyActivity toEntity(GroupBuyActivityPO po) {
        if (po == null) {
            return null;
        }
        GroupBuyActivity entity = new GroupBuyActivity();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<GroupBuyActivity> toActivities(List<GroupBuyActivityPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(ActivityPOConverter::toEntity).toList();
    }

    public static GroupBuyActivityPO toPO(GroupBuyActivity entity) {
        if (entity == null) {
            return null;
        }
        GroupBuyActivityPO po = new GroupBuyActivityPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static GroupBuyDiscount toEntity(GroupBuyDiscountPO po) {
        if (po == null) {
            return null;
        }
        GroupBuyDiscount entity = new GroupBuyDiscount();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static GroupBuyDiscountPO toPO(GroupBuyDiscount entity) {
        if (entity == null) {
            return null;
        }
        GroupBuyDiscountPO po = new GroupBuyDiscountPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static List<GroupBuyDiscount> toDiscounts(List<GroupBuyDiscountPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(ActivityPOConverter::toEntity).toList();
    }

    public static GroupBuyMarketSku toEntity(GroupBuyMarketSkuPO po) {
        if (po == null) {
            return null;
        }
        GroupBuyMarketSku entity = new GroupBuyMarketSku();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<GroupBuyMarketSku> toMarketSkus(List<GroupBuyMarketSkuPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(ActivityPOConverter::toEntity).toList();
    }

    public static SourceChannelSkuActivity toEntity(SourceChannelSkuActivityPO po) {
        if (po == null) {
            return null;
        }
        SourceChannelSkuActivity entity = new SourceChannelSkuActivity();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<SourceChannelSkuActivity> toSourceChannels(List<SourceChannelSkuActivityPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(ActivityPOConverter::toEntity).toList();
    }

    public static GroupBuyStock toEntity(GroupBuyStockPO po) {
        if (po == null) {
            return null;
        }
        GroupBuyStock entity = new GroupBuyStock();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<GroupBuyStock> toStocks(List<GroupBuyStockPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(ActivityPOConverter::toEntity).toList();
    }

    public static GroupBuyStockPO toPO(GroupBuyStock entity) {
        if (entity == null) {
            return null;
        }
        GroupBuyStockPO po = new GroupBuyStockPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static GroupBuyStockFlowPO toPO(GroupBuyStockFlow entity) {
        if (entity == null) {
            return null;
        }
        GroupBuyStockFlowPO po = new GroupBuyStockFlowPO();
        BeanUtils.copyProperties(entity, po, "flowType");
        po.setFlowType(enumName(entity.getFlowType()));
        return po;
    }

    public static GroupBuyTeamPO toPO(GroupBuyTeam entity) {
        if (entity == null) {
            return null;
        }
        GroupBuyTeamPO po = new GroupBuyTeamPO();
        BeanUtils.copyProperties(entity, po, "teamStatus");
        po.setTeamStatus(enumName(entity.getTeamStatus()));
        return po;
    }

    public static GroupBuyTeam toEntity(GroupBuyTeamPO po) {
        if (po == null) {
            return null;
        }
        GroupBuyTeam entity = new GroupBuyTeam();
        BeanUtils.copyProperties(po, entity, "teamStatus");
        entity.setTeamStatus(enumValue(GroupBuyTeamStatus.class, po.getTeamStatus()));
        return entity;
    }

    public static GroupBuyOrderLockPO toPO(GroupBuyOrderLock entity) {
        if (entity == null) {
            return null;
        }
        GroupBuyOrderLockPO po = new GroupBuyOrderLockPO();
        BeanUtils.copyProperties(entity, po, "lockStatus");
        po.setLockStatus(enumName(entity.getLockStatus()));
        return po;
    }

    public static GroupBuyOrderLock toEntity(GroupBuyOrderLockPO po) {
        if (po == null) {
            return null;
        }
        GroupBuyOrderLock entity = new GroupBuyOrderLock();
        BeanUtils.copyProperties(po, entity, "lockStatus");
        entity.setLockStatus(enumValue(GroupBuyLockStatus.class, po.getLockStatus()));
        return entity;
    }

    public static GroupBuyTeamDetail toEntity(GroupBuyTeamDetailPO po) {
        if (po == null) {
            return null;
        }
        GroupBuyTeamDetail entity = new GroupBuyTeamDetail();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<GroupBuyTeamDetail> toTeamDetails(List<GroupBuyTeamDetailPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(ActivityPOConverter::toEntity).toList();
    }

    public static GroupBuyTeamStatistic toEntity(GroupBuyTeamStatisticPO po) {
        if (po == null) {
            return null;
        }
        GroupBuyTeamStatistic entity = new GroupBuyTeamStatistic();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> enumType, String value) {
        return value == null ? null : Enum.valueOf(enumType, value);
    }
}















