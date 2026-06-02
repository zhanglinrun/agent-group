package com.linrun.infrastructure.converter;

import com.linrun.domain.academic.model.AcademicArtifact;
import com.linrun.domain.academic.model.AcademicFile;
import com.linrun.domain.academic.model.AcademicMessage;
import com.linrun.domain.academic.model.AcademicSession;
import com.linrun.infrastructure.po.AcademicArtifactPO;
import com.linrun.infrastructure.po.AcademicFilePO;
import com.linrun.infrastructure.po.AcademicMessagePO;
import com.linrun.infrastructure.po.AcademicSessionPO;
import org.springframework.beans.BeanUtils;

import java.util.List;

public final class AcademicPOConverter {

    private AcademicPOConverter() {
    }

    public static AcademicSessionPO toPO(AcademicSession entity) {
        if (entity == null) {
            return null;
        }
        AcademicSessionPO po = new AcademicSessionPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static AcademicSession toEntity(AcademicSessionPO po) {
        if (po == null) {
            return null;
        }
        AcademicSession entity = new AcademicSession();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AcademicSession> toSessions(List<AcademicSessionPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AcademicPOConverter::toEntity).toList();
    }

    public static AcademicMessagePO toPO(AcademicMessage entity) {
        if (entity == null) {
            return null;
        }
        AcademicMessagePO po = new AcademicMessagePO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static AcademicMessage toEntity(AcademicMessagePO po) {
        if (po == null) {
            return null;
        }
        AcademicMessage entity = new AcademicMessage();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AcademicMessage> toMessages(List<AcademicMessagePO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AcademicPOConverter::toEntity).toList();
    }

    public static AcademicFilePO toPO(AcademicFile entity) {
        if (entity == null) {
            return null;
        }
        AcademicFilePO po = new AcademicFilePO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static AcademicFile toEntity(AcademicFilePO po) {
        if (po == null) {
            return null;
        }
        AcademicFile entity = new AcademicFile();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AcademicFile> toFiles(List<AcademicFilePO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AcademicPOConverter::toEntity).toList();
    }

    public static AcademicArtifactPO toPO(AcademicArtifact entity) {
        if (entity == null) {
            return null;
        }
        AcademicArtifactPO po = new AcademicArtifactPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static AcademicArtifact toEntity(AcademicArtifactPO po) {
        if (po == null) {
            return null;
        }
        AcademicArtifact entity = new AcademicArtifact();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AcademicArtifact> toArtifacts(List<AcademicArtifactPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AcademicPOConverter::toEntity).toList();
    }
}
