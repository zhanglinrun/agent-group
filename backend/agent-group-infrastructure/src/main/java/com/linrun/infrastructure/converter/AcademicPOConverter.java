package com.linrun.infrastructure.converter;

import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.model.AcademicLlmInvocation;
import com.linrun.domain.academic.ledger.model.AcademicToolInvocation;
import com.linrun.domain.academic.model.AcademicArtifact;
import com.linrun.domain.academic.model.AcademicFile;
import com.linrun.domain.academic.model.AcademicMessage;
import com.linrun.domain.academic.model.AcademicSession;
import com.linrun.infrastructure.po.AcademicAgentRunPO;
import com.linrun.infrastructure.po.AcademicArtifactPO;
import com.linrun.infrastructure.po.AcademicFilePO;
import com.linrun.infrastructure.po.AcademicLlmInvocationPO;
import com.linrun.infrastructure.po.AcademicMessagePO;
import com.linrun.infrastructure.po.AcademicSessionPO;
import com.linrun.infrastructure.po.AcademicToolInvocationPO;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
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
        po.setArtifactId(blank(po.getArtifactId()));
        po.setSessionId(blank(po.getSessionId()));
        po.setUserId(blank(po.getUserId()));
        po.setRunId(blank(po.getRunId()));
        po.setToolInvocationId(blank(po.getToolInvocationId()));
        po.setSourceType(text(po.getSourceType(), "AGENT"));
        po.setSourceName(blank(po.getSourceName()));
        po.setArtifactType(blank(po.getArtifactType()));
        po.setTitle(blank(po.getTitle()));
        po.setContent(blank(po.getContent()));
        po.setDownloadUrl(blank(po.getDownloadUrl()));
        po.setCreateTime(time(po.getCreateTime()));
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

    public static AcademicAgentRunPO toPO(AcademicAgentRun entity) {
        if (entity == null) {
            return null;
        }
        AcademicAgentRunPO po = new AcademicAgentRunPO();
        BeanUtils.copyProperties(entity, po);
        po.setRunId(blank(po.getRunId()));
        po.setSessionId(blank(po.getSessionId()));
        po.setRequestId(blank(po.getRequestId()));
        po.setUserId(blank(po.getUserId()));
        po.setTaskType(blank(po.getTaskType()));
        po.setQuestion(blank(po.getQuestion()));
        po.setStatus(text(po.getStatus(), AcademicAgentRun.STATUS_RUNNING));
        po.setModelName(blank(po.getModelName()));
        po.setFinalSummary(blank(po.getFinalSummary()));
        po.setErrorCode(blank(po.getErrorCode()));
        po.setErrorMessage(blank(po.getErrorMessage()));
        po.setStartedAt(time(po.getStartedAt()));
        po.setDurationMillis(nonNegative(po.getDurationMillis()));
        return po;
    }

    public static AcademicAgentRun toEntity(AcademicAgentRunPO po) {
        if (po == null) {
            return null;
        }
        AcademicAgentRun entity = new AcademicAgentRun();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AcademicAgentRun> toRuns(List<AcademicAgentRunPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AcademicPOConverter::toEntity).toList();
    }

    public static AcademicLlmInvocationPO toPO(AcademicLlmInvocation entity) {
        if (entity == null) {
            return null;
        }
        AcademicLlmInvocationPO po = new AcademicLlmInvocationPO();
        BeanUtils.copyProperties(entity, po);
        po.setInvocationId(blank(po.getInvocationId()));
        po.setRunId(blank(po.getRunId()));
        po.setRequestId(blank(po.getRequestId()));
        po.setSessionId(blank(po.getSessionId()));
        po.setUserId(blank(po.getUserId()));
        po.setModelName(blank(po.getModelName()));
        po.setPromptSummary(blank(po.getPromptSummary()));
        po.setResponseText(blank(po.getResponseText()));
        po.setStatus(text(po.getStatus(), AcademicAgentRun.STATUS_RUNNING));
        po.setPromptTokens(nonNegative(po.getPromptTokens()));
        po.setCompletionTokens(nonNegative(po.getCompletionTokens()));
        po.setTotalTokens(nonNegative(po.getTotalTokens()));
        po.setFallbackUsed(Boolean.TRUE.equals(po.getFallbackUsed()));
        po.setErrorMessage(blank(po.getErrorMessage()));
        po.setStartedAt(time(po.getStartedAt()));
        po.setLatencyMillis(nonNegative(po.getLatencyMillis()));
        return po;
    }

    public static AcademicLlmInvocation toEntity(AcademicLlmInvocationPO po) {
        if (po == null) {
            return null;
        }
        AcademicLlmInvocation entity = new AcademicLlmInvocation();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AcademicLlmInvocation> toLlmInvocations(List<AcademicLlmInvocationPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AcademicPOConverter::toEntity).toList();
    }

    public static AcademicToolInvocationPO toPO(AcademicToolInvocation entity) {
        if (entity == null) {
            return null;
        }
        AcademicToolInvocationPO po = new AcademicToolInvocationPO();
        BeanUtils.copyProperties(entity, po);
        po.setInvocationId(blank(po.getInvocationId()));
        po.setRunId(blank(po.getRunId()));
        po.setRequestId(blank(po.getRequestId()));
        po.setSessionId(blank(po.getSessionId()));
        po.setUserId(blank(po.getUserId()));
        po.setToolCallId(blank(po.getToolCallId()));
        po.setToolName(blank(po.getToolName()));
        po.setAction(blank(po.getAction()));
        po.setArgumentsJson(blank(po.getArgumentsJson()));
        po.setResultSummary(blank(po.getResultSummary()));
        po.setResultJson(blank(po.getResultJson()));
        po.setStatus(text(po.getStatus(), AcademicAgentRun.STATUS_RUNNING));
        po.setRetryCount(nonNegative(po.getRetryCount()));
        po.setErrorMessage(blank(po.getErrorMessage()));
        po.setStartedAt(time(po.getStartedAt()));
        po.setLatencyMillis(nonNegative(po.getLatencyMillis()));
        return po;
    }

    public static AcademicToolInvocation toEntity(AcademicToolInvocationPO po) {
        if (po == null) {
            return null;
        }
        AcademicToolInvocation entity = new AcademicToolInvocation();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AcademicToolInvocation> toToolInvocations(List<AcademicToolInvocationPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AcademicPOConverter::toEntity).toList();
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Long nonNegative(Long value) {
        return value == null || value < 0 ? 0L : value;
    }

    private static Integer nonNegative(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private static LocalDateTime time(LocalDateTime value) {
        return value == null ? LocalDateTime.now() : value;
    }
}
