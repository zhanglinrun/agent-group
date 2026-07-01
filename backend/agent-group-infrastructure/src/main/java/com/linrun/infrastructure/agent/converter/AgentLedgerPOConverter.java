package com.linrun.infrastructure.agent.converter;

import com.linrun.domain.agent.ledger.model.AgentRun;
import com.linrun.domain.agent.ledger.model.AgentLlmInvocation;
import com.linrun.domain.agent.ledger.model.AgentToolInvocation;
import com.linrun.domain.agent.model.AgentArtifact;
import com.linrun.domain.agent.model.AgentFile;
import com.linrun.domain.agent.model.AgentMessage;
import com.linrun.domain.agent.model.AgentSession;
import com.linrun.infrastructure.po.AgentRunPO;
import com.linrun.infrastructure.po.AgentArtifactPO;
import com.linrun.infrastructure.po.AgentFilePO;
import com.linrun.infrastructure.po.AgentLlmInvocationPO;
import com.linrun.infrastructure.po.AgentMessagePO;
import com.linrun.infrastructure.po.AgentSessionPO;
import com.linrun.infrastructure.po.AgentToolInvocationPO;
import org.springframework.beans.BeanUtils;

import java.time.LocalDateTime;
import java.util.List;

public final class AgentLedgerPOConverter {

    private AgentLedgerPOConverter() {
    }

    public static AgentSessionPO toPO(AgentSession entity) {
        if (entity == null) {
            return null;
        }
        AgentSessionPO po = new AgentSessionPO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static AgentSession toEntity(AgentSessionPO po) {
        if (po == null) {
            return null;
        }
        AgentSession entity = new AgentSession();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AgentSession> toSessions(List<AgentSessionPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentLedgerPOConverter::toEntity).toList();
    }

    public static AgentMessagePO toPO(AgentMessage entity) {
        if (entity == null) {
            return null;
        }
        AgentMessagePO po = new AgentMessagePO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static AgentMessage toEntity(AgentMessagePO po) {
        if (po == null) {
            return null;
        }
        AgentMessage entity = new AgentMessage();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AgentMessage> toMessages(List<AgentMessagePO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentLedgerPOConverter::toEntity).toList();
    }

    public static AgentFilePO toPO(AgentFile entity) {
        if (entity == null) {
            return null;
        }
        AgentFilePO po = new AgentFilePO();
        BeanUtils.copyProperties(entity, po);
        return po;
    }

    public static AgentFile toEntity(AgentFilePO po) {
        if (po == null) {
            return null;
        }
        AgentFile entity = new AgentFile();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AgentFile> toFiles(List<AgentFilePO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentLedgerPOConverter::toEntity).toList();
    }

    public static AgentArtifactPO toPO(AgentArtifact entity) {
        if (entity == null) {
            return null;
        }
        AgentArtifactPO po = new AgentArtifactPO();
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

    public static AgentArtifact toEntity(AgentArtifactPO po) {
        if (po == null) {
            return null;
        }
        AgentArtifact entity = new AgentArtifact();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AgentArtifact> toArtifacts(List<AgentArtifactPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentLedgerPOConverter::toEntity).toList();
    }

    public static AgentRunPO toPO(AgentRun entity) {
        if (entity == null) {
            return null;
        }
        AgentRunPO po = new AgentRunPO();
        BeanUtils.copyProperties(entity, po);
        po.setRunId(blank(po.getRunId()));
        po.setSessionId(blank(po.getSessionId()));
        po.setProjectId(blank(po.getProjectId()));
        po.setRequestId(blank(po.getRequestId()));
        po.setUserId(blank(po.getUserId()));
        po.setTaskType(blank(po.getTaskType()));
        po.setQuestion(blank(po.getQuestion()));
        po.setStatus(text(po.getStatus(), AgentRun.STATUS_RUNNING));
        po.setModelName(blank(po.getModelName()));
        po.setFinalSummary(blank(po.getFinalSummary()));
        po.setErrorCode(blank(po.getErrorCode()));
        po.setErrorMessage(blank(po.getErrorMessage()));
        po.setStartedAt(time(po.getStartedAt()));
        po.setDurationMillis(nonNegative(po.getDurationMillis()));
        return po;
    }

    public static AgentRun toEntity(AgentRunPO po) {
        if (po == null) {
            return null;
        }
        AgentRun entity = new AgentRun();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AgentRun> toRuns(List<AgentRunPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentLedgerPOConverter::toEntity).toList();
    }

    public static AgentLlmInvocationPO toPO(AgentLlmInvocation entity) {
        if (entity == null) {
            return null;
        }
        AgentLlmInvocationPO po = new AgentLlmInvocationPO();
        BeanUtils.copyProperties(entity, po);
        po.setInvocationId(blank(po.getInvocationId()));
        po.setRunId(blank(po.getRunId()));
        po.setRequestId(blank(po.getRequestId()));
        po.setSessionId(blank(po.getSessionId()));
        po.setUserId(blank(po.getUserId()));
        po.setModelName(blank(po.getModelName()));
        po.setPromptSummary(blank(po.getPromptSummary()));
        po.setResponseText(blank(po.getResponseText()));
        po.setStatus(text(po.getStatus(), AgentRun.STATUS_RUNNING));
        po.setPromptTokens(nonNegative(po.getPromptTokens()));
        po.setCompletionTokens(nonNegative(po.getCompletionTokens()));
        po.setTotalTokens(nonNegative(po.getTotalTokens()));
        po.setFallbackUsed(Boolean.TRUE.equals(po.getFallbackUsed()));
        po.setErrorMessage(blank(po.getErrorMessage()));
        po.setStartedAt(time(po.getStartedAt()));
        po.setLatencyMillis(nonNegative(po.getLatencyMillis()));
        return po;
    }

    public static AgentLlmInvocation toEntity(AgentLlmInvocationPO po) {
        if (po == null) {
            return null;
        }
        AgentLlmInvocation entity = new AgentLlmInvocation();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AgentLlmInvocation> toLlmInvocations(List<AgentLlmInvocationPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentLedgerPOConverter::toEntity).toList();
    }

    public static AgentToolInvocationPO toPO(AgentToolInvocation entity) {
        if (entity == null) {
            return null;
        }
        AgentToolInvocationPO po = new AgentToolInvocationPO();
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
        po.setStatus(text(po.getStatus(), AgentRun.STATUS_RUNNING));
        po.setRetryCount(nonNegative(po.getRetryCount()));
        po.setErrorMessage(blank(po.getErrorMessage()));
        po.setStartedAt(time(po.getStartedAt()));
        po.setLatencyMillis(nonNegative(po.getLatencyMillis()));
        return po;
    }

    public static AgentToolInvocation toEntity(AgentToolInvocationPO po) {
        if (po == null) {
            return null;
        }
        AgentToolInvocation entity = new AgentToolInvocation();
        BeanUtils.copyProperties(po, entity);
        return entity;
    }

    public static List<AgentToolInvocation> toToolInvocations(List<AgentToolInvocationPO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        return poList.stream().map(AgentLedgerPOConverter::toEntity).toList();
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















