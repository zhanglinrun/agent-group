package com.linrun.infrastructure.converter;

import com.linrun.domain.agent.conversation.model.GuideConversationMessage;
import com.linrun.domain.agent.conversation.model.GuideDecisionSnapshot;
import com.linrun.domain.agent.conversation.model.GuideMessageRole;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocument;
import com.linrun.domain.agent.knowledge.model.KnowledgeDocumentStatus;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragment;
import com.linrun.domain.agent.knowledge.model.KnowledgeFragmentStatus;
import com.linrun.domain.agent.quality.model.GuideEvaluationFeedback;
import com.linrun.domain.agent.quality.model.GuideEvaluationItemResult;
import com.linrun.domain.agent.quality.model.GuideEvaluationReport;
import com.linrun.infrastructure.po.GuideConversationMessagePO;
import com.linrun.infrastructure.po.GuideDecisionSnapshotPO;
import com.linrun.infrastructure.po.GuideEvaluationFeedbackPO;
import com.linrun.infrastructure.po.GuideEvaluationItemResultPO;
import com.linrun.infrastructure.po.GuideEvaluationReportPO;
import com.linrun.infrastructure.po.GuideProductPO;
import com.linrun.infrastructure.po.GuideReferencePO;
import com.linrun.infrastructure.po.KnowledgeDocumentPO;
import com.linrun.infrastructure.po.KnowledgeFragmentPO;
import org.springframework.beans.BeanUtils;

import java.util.List;

public final class AgentPOConverter {

    private AgentPOConverter() {
    }

    public static GuideConversationMessagePO toPO(GuideConversationMessage entity) {
        if (entity == null) {
            return null;
        }
        GuideConversationMessagePO po = new GuideConversationMessagePO();
        BeanUtils.copyProperties(entity, po, "role");
        po.setRole(enumName(entity.getRole()));
        return po;
    }

    public static GuideConversationMessage toEntity(GuideConversationMessagePO po) {
        if (po == null) {
            return null;
        }
        GuideConversationMessage entity = new GuideConversationMessage();
        BeanUtils.copyProperties(po, entity, "role");
        entity.setRole(enumValue(GuideMessageRole.class, po.getRole()));
        return entity;
    }

    public static List<GuideConversationMessage> toGuideConversationMessages(List<GuideConversationMessagePO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentPOConverter::toEntity).toList();
    }

    public static GuideDecisionSnapshotPO toPO(GuideDecisionSnapshot entity) {
        if (entity == null) {
            return null;
        }
        GuideDecisionSnapshotPO po = new GuideDecisionSnapshotPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static GuideDecisionSnapshot toEntity(GuideDecisionSnapshotPO po) {
        if (po == null) {
            return null;
        }
        GuideDecisionSnapshot entity = new GuideDecisionSnapshot();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static GuideProduct toEntity(GuideProductPO po) {
        if (po == null) {
            return null;
        }
        GuideProduct entity = new GuideProduct();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<GuideProduct> toGuideProducts(List<GuideProductPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentPOConverter::toEntity).toList();
    }

    public static GuideReference toEntity(GuideReferencePO po) {
        if (po == null) {
            return null;
        }
        GuideReference entity = new GuideReference();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<GuideReference> toGuideReferences(List<GuideReferencePO> poList) {
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

    public static GuideEvaluationReportPO toPO(GuideEvaluationReport entity) {
        if (entity == null) {
            return null;
        }
        GuideEvaluationReportPO po = new GuideEvaluationReportPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static GuideEvaluationReport toEntity(GuideEvaluationReportPO po) {
        if (po == null) {
            return null;
        }
        GuideEvaluationReport entity = new GuideEvaluationReport();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static GuideEvaluationItemResultPO toPO(GuideEvaluationItemResult entity) {
        if (entity == null) {
            return null;
        }
        GuideEvaluationItemResultPO po = new GuideEvaluationItemResultPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static List<GuideEvaluationItemResultPO> toGuideEvaluationItemPOList(List<GuideEvaluationItemResult> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream().map(AgentPOConverter::toPO).toList();
    }

    public static GuideEvaluationItemResult toEntity(GuideEvaluationItemResultPO po) {
        if (po == null) {
            return null;
        }
        GuideEvaluationItemResult entity = new GuideEvaluationItemResult();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<GuideEvaluationItemResult> toGuideEvaluationItems(List<GuideEvaluationItemResultPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentPOConverter::toEntity).toList();
    }

    public static GuideEvaluationFeedbackPO toPO(GuideEvaluationFeedback entity) {
        if (entity == null) {
            return null;
        }
        GuideEvaluationFeedbackPO po = new GuideEvaluationFeedbackPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static List<GuideEvaluationFeedbackPO> toGuideEvaluationFeedbackPOList(List<GuideEvaluationFeedback> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream().map(AgentPOConverter::toPO).toList();
    }

    public static GuideEvaluationFeedback toEntity(GuideEvaluationFeedbackPO po) {
        if (po == null) {
            return null;
        }
        GuideEvaluationFeedback entity = new GuideEvaluationFeedback();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<GuideEvaluationFeedback> toGuideEvaluationFeedbacks(List<GuideEvaluationFeedbackPO> poList) {
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
