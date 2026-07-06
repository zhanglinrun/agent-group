package com.linrun.infrastructure.quota.converter;

import com.linrun.domain.quota.model.QuotaOrderSnapshot;
import com.linrun.domain.quota.model.QuotaProduct;
import com.linrun.infrastructure.po.QuotaOrderSnapshotPO;
import com.linrun.infrastructure.po.QuotaProductPO;
import org.springframework.beans.BeanUtils;

import java.util.List;

public final class QuotaPOConverter {

    private QuotaPOConverter() {
    }

    public static QuotaOrderSnapshotPO toPO(QuotaOrderSnapshot entity) {
        if (entity == null) {
            return null;
        }
        QuotaOrderSnapshotPO po = new QuotaOrderSnapshotPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static QuotaOrderSnapshot toEntity(QuotaOrderSnapshotPO po) {
        if (po == null) {
            return null;
        }
        QuotaOrderSnapshot entity = new QuotaOrderSnapshot();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static QuotaProduct toEntity(QuotaProductPO po) {
        if (po == null) {
            return null;
        }
        QuotaProduct entity = new QuotaProduct();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<QuotaProduct> toQuotaProducts(List<QuotaProductPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(QuotaPOConverter::toEntity).toList();
    }
}
