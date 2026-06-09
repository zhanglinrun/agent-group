package com.linrun.infrastructure.agent.converter;

import com.linrun.domain.agent.conversation.model.QuotaOrderSnapshot;
import com.linrun.domain.agent.conversation.model.QuotaProduct;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocument;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocumentStatus;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragmentStatus;
import com.linrun.infrastructure.po.QuotaOrderSnapshotPO;
import com.linrun.infrastructure.po.QuotaProductPO;
import com.linrun.infrastructure.po.KnowledgeDocumentPO;
import com.linrun.infrastructure.po.KnowledgeFragmentPO;
import org.springframework.beans.BeanUtils;

import java.util.List;

public final class AgentPOConverter {

    private AgentPOConverter() {
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
        return poList.stream().map(AgentPOConverter::toEntity).toList();
    }

    public static KnowledgeDocumentPO toPO(KnowledgeDocument entity) {
        if (entity == null) {
            return null;
        }
        KnowledgeDocumentPO po = new KnowledgeDocumentPO();
        BeanUtils.copyProperties(entity, po, "documentStatus");
        po.setDocumentStatus(enumName(entity.getDocumentStatus()));
        return po;
    }

    public static KnowledgeDocument toEntity(KnowledgeDocumentPO po) {
        if (po == null) {
            return null;
        }
        KnowledgeDocument entity = new KnowledgeDocument();
        BeanUtils.copyProperties(po, entity, "documentStatus");
        entity.setDocumentStatus(enumValue(KnowledgeDocumentStatus.class, po.getDocumentStatus()));
        return entity;
    }

    public static List<KnowledgeDocument> toKnowledgeDocuments(List<KnowledgeDocumentPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentPOConverter::toEntity).toList();
    }

    public static KnowledgeFragmentPO toPO(KnowledgeFragment entity) {
        if (entity == null) {
            return null;
        }
        KnowledgeFragmentPO po = new KnowledgeFragmentPO();
        BeanUtils.copyProperties(entity, po, "fragmentStatus");
        po.setFragmentStatus(enumName(entity.getFragmentStatus()));
        return po;
    }

    public static List<KnowledgeFragmentPO> toKnowledgeFragmentPOList(List<KnowledgeFragment> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream().map(AgentPOConverter::toPO).toList();
    }

    public static KnowledgeFragment toEntity(KnowledgeFragmentPO po) {
        if (po == null) {
            return null;
        }
        KnowledgeFragment entity = new KnowledgeFragment();
        BeanUtils.copyProperties(po, entity, "fragmentStatus");
        entity.setFragmentStatus(enumValue(KnowledgeFragmentStatus.class, po.getFragmentStatus()));
        return entity;
    }

    public static List<KnowledgeFragment> toKnowledgeFragments(List<KnowledgeFragmentPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentPOConverter::toEntity).toList();
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> enumType, String value) {
        return value == null ? null : Enum.valueOf(enumType, value);
    }
}















