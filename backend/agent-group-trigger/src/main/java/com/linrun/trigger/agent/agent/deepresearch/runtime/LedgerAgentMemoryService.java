package com.linrun.trigger.agent.agent.deepresearch.runtime;

import com.linrun.api.dto.AgentSessionDetailResponse;
import com.linrun.domain.agent.ledger.service.AgentExecutionLedgerService;
import com.linrun.domain.agent.memory.model.UserAgentMemory;
import com.linrun.domain.agent.memory.service.UserAgentMemoryService;
import com.linrun.trigger.agent.agent.deepresearch.support.AgentResearchContextPolicy;
import com.linrun.trigger.config.AgentDeepRuntimeProperties;
import org.springframework.util.StringUtils;

import java.util.List;

public class LedgerAgentMemoryService implements AgentMemoryService {

    private final AgentExecutionLedgerService ledgerService;
    private final UserAgentMemoryService userMemoryService;
    private final AgentDeepRuntimeProperties deepRuntimeProperties;

    public LedgerAgentMemoryService(AgentExecutionLedgerService ledgerService) {
        this(ledgerService, null, null);
    }

    public LedgerAgentMemoryService(AgentExecutionLedgerService ledgerService,
                                    UserAgentMemoryService userMemoryService) {
        this(ledgerService, userMemoryService, null);
    }

    public LedgerAgentMemoryService(AgentExecutionLedgerService ledgerService,
                                    UserAgentMemoryService userMemoryService,
                                    AgentDeepRuntimeProperties deepRuntimeProperties) {
        this.ledgerService = ledgerService;
        this.userMemoryService = userMemoryService;
        this.deepRuntimeProperties = deepRuntimeProperties;
    }

    @Override
    public AgentMemorySnapshot load(String userId,
                                    String sessionId,
                                    String runId,
                                    String currentRequestId) {
        return load(userId, sessionId, runId, currentRequestId, "");
    }

    @Override
    public AgentMemorySnapshot load(String userId,
                                    String sessionId,
                                    String runId,
                                    String currentRequestId,
                                    String question) {
        List<String> longTerm = loadLongTerm(userId, question);
        if (ledgerService == null || !StringUtils.hasText(userId) || !StringUtils.hasText(sessionId)) {
            return new AgentMemorySnapshot(userId, sessionId, List.of(), List.of(), longTerm, !longTerm.isEmpty());
        }
        AgentSessionDetailResponse.MemorySnapshot memory =
                ledgerService.querySessionMemory(userId, sessionId, currentRequestId, 6);
        List<String> shortTerm = List.of(limit(memory.getSummary(), 600), limit(memory.getHistoryDialogue(), 1200));
        List<String> taskMemory = memory.getRuns().stream()
                .map(run -> "%s [%s] %s".formatted(run.getRunId(), run.getStatus(), limit(run.getFinalSummary(), 240)))
                .toList();
        return new AgentMemorySnapshot(userId, sessionId, shortTerm, taskMemory, longTerm, !longTerm.isEmpty());
    }

    private List<String> loadLongTerm(String userId, String question) {
        if (userMemoryService == null || !StringUtils.hasText(userId)) {
            return List.of();
        }
        try {
            return userMemoryService.queryEnabled(userId, 6).stream()
                    .filter(memory -> AgentResearchContextPolicy.shouldInjectMemory(
                            memory, question, deepRuntimeProperties))
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
