package com.linrun.trigger.agent.agent.deepresearch.runtime;

import com.linrun.api.dto.AcademicSessionDetailResponse;
import com.linrun.domain.academic.ledger.service.AcademicExecutionLedgerService;
import com.linrun.domain.academic.memory.model.UserAgentMemory;
import com.linrun.domain.academic.memory.service.UserAgentMemoryService;
import org.springframework.util.StringUtils;

import java.util.List;

public class LedgerAgentMemoryService implements AgentMemoryService {

    private final AcademicExecutionLedgerService ledgerService;
    private final UserAgentMemoryService userMemoryService;

    public LedgerAgentMemoryService(AcademicExecutionLedgerService ledgerService) {
        this(ledgerService, null);
    }

    public LedgerAgentMemoryService(AcademicExecutionLedgerService ledgerService,
                                    UserAgentMemoryService userMemoryService) {
        this.ledgerService = ledgerService;
        this.userMemoryService = userMemoryService;
    }

    @Override
    public AgentMemorySnapshot load(String userId,
                                    String sessionId,
                                    String runId,
                                    String currentRequestId) {
        List<String> longTerm = loadLongTerm(userId);
        if (ledgerService == null || !StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) {
            return new AgentMemorySnapshot(userId, sessionId, List.of(), List.of(), longTerm, !longTerm.isEmpty());
        }
        AcademicSessionDetailResponse.MemorySnapshot memory =
                ledgerService.querySessionMemory(userId, sessionId, currentRequestId, 6);
        List<String> shortTerm = List.of(limit(memory.getSummary(), 600), limit(memory.getHistoryDialogue(), 1200));
        List<String> taskMemory = memory.getRuns().stream()
                .map(run -> "%s [%s] %s".formatted(run.getRunId(), run.getStatus(), limit(run.getFinalSummary(), 240)))
                .toList();
        return new AgentMemorySnapshot(userId, sessionId, shortTerm, taskMemory, longTerm, !longTerm.isEmpty());
    }

    private List<String> loadLongTerm(String userId) {
        if (userMemoryService == null || !StringUtils.hasText(userId)) {
            return List.of();
        }
        try {
            return userMemoryService.queryEnabled(userId, 6).stream()
                    .map(this::formatLongTerm)
                    .filter(StringUtils::hasText)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String formatLongTerm(UserAgentMemory memory) {
        if (memory == null || !StringUtils.hasText(memory.getContent())) {
            return "";
        }
        String type = StringUtils.hasText(memory.getMemoryType()) ? memory.getMemoryType() : "preference";
        return type + ": " + limit(memory.getContent(), 500);
    }

    private static String limit(String text, int maxChars) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0, maxChars) + "...";
    }

}
