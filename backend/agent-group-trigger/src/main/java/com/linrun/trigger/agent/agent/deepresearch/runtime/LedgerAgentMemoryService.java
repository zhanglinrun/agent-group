package com.linrun.trigger.agent.agent.deepresearch.runtime;

import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.domain.academic.ledger.service.AcademicExecutionLedgerService;
import org.springframework.util.StringUtils;

import java.util.List;

public class LedgerAgentMemoryService implements AgentMemoryService {

    private final AcademicExecutionLedgerService ledgerService;

    public LedgerAgentMemoryService(AcademicExecutionLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Override
    public AgentMemorySnapshot load(String tenantId,
                                    String userId,
                                    String sessionId,
                                    String runId,
                                    String currentRequestId,
                                    boolean longTermEnabled) {
        if (ledgerService == null || !StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) {
            return AgentMemorySnapshot.empty(tenantId, userId, sessionId);
        }
        AcademicSessionDetailResponse.MemorySnapshot memory =
                ledgerService.querySessionMemory(userId, sessionId, currentRequestId, 6);
        List<String> shortTerm = List.of(limit(memory.getSummary(), 600), limit(memory.getHistoryDialogue(), 1200));
        List<String> taskMemory = memory.getRuns().stream()
                .map(run -> "%s [%s] %s".formatted(run.getRunId(), run.getStatus(), limit(run.getFinalSummary(), 240)))
                .toList();
        List<String> longTerm = longTermEnabled
                ? List.of("tenant=" + safe(tenantId), "user=" + safe(userId), "privacy=enabled")
                : List.of();
        return new AgentMemorySnapshot(tenantId, userId, sessionId, shortTerm, taskMemory, longTerm, longTermEnabled);
    }

    private static String limit(String text, int maxChars) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0, maxChars) + "...";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
