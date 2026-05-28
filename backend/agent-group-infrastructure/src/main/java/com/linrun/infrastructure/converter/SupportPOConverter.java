package com.linrun.infrastructure.converter;

import com.linrun.domain.support.config.model.DynamicConfig;
import com.linrun.domain.tag.model.CrowdTag;
import com.linrun.domain.tag.model.CrowdTagJob;
import com.linrun.infrastructure.po.CrowdTagPO;
import com.linrun.infrastructure.po.CrowdTagJobPO;
import com.linrun.infrastructure.po.DynamicConfigPO;
import org.springframework.beans.BeanUtils;

import java.util.List;

public final class SupportPOConverter {

    private SupportPOConverter() {
    }

    public static DynamicConfigPO toPO(DynamicConfig entity) {
        if (entity == null) {
            return null;
        }
        DynamicConfigPO po = new DynamicConfigPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static DynamicConfig toEntity(DynamicConfigPO po) {
        if (po == null) {
            return null;
        }
        DynamicConfig entity = new DynamicConfig();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static CrowdTagJob toEntity(CrowdTagJobPO po) {
        if (po == null) {
            return null;
        }
        CrowdTagJob entity = new CrowdTagJob();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static CrowdTag toEntity(CrowdTagPO po) {
        if (po == null) {
            return null;
        }
        CrowdTag entity = new CrowdTag();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<CrowdTag> toCrowdTags(List<CrowdTagPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(SupportPOConverter::toEntity).toList();
    }

    public static List<CrowdTagJob> toCrowdTagJobs(List<CrowdTagJobPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(SupportPOConverter::toEntity).toList();
    }
}
