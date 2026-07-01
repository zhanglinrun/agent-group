package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AgentWorkspaceDataCatalogResponse;
import com.linrun.api.dto.AgentWorkspaceDataHistoryResponse;
import com.linrun.api.dto.AgentWorkspaceDataRunRequest;
import com.linrun.api.dto.AgentWorkspaceDataRunResponse;
import com.linrun.domain.agent.adapter.AgentRepository;
import com.linrun.domain.agent.ledger.model.AgentRun;
import com.linrun.domain.agent.ledger.service.AgentExecutionLedgerService;
import com.linrun.domain.agent.model.AgentSession;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.port.AgentDataAnalysisPort;
import com.linrun.domain.agent.runtime.tool.port.AgentNl2SqlPort;
import com.linrun.domain.agent.runtime.tool.port.AgentTableRagPort;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.quota.service.UserQuotaService;
import com.linrun.domain.quota.model.TokenUsageMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentWorkspaceDataServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldRunDataWorkspaceToolsAndRecordLedger() {
        UserAccountService userAccountService = mock(UserAccountService.class);
        UserQuotaService userQuotaService = mock(UserQuotaService.class);
        AgentRepository repository = mock(AgentRepository.class);
        AgentExecutionLedgerService ledgerService = mock(AgentExecutionLedgerService.class);
        ObjectProvider<AgentDataAnalysisPort> dataProvider = mock(ObjectProvider.class);
        ObjectProvider<AgentTableRagPort> tableProvider = mock(ObjectProvider.class);
        ObjectProvider<AgentNl2SqlPort> sqlProvider = mock(ObjectProvider.class);
        UserAccount user = user("U1001");
        AgentRun run = run("RUN1001");
        AgentTableRagPort tablePort = request -> new AgentTableRagPort.AgentTableRagResult(
                true,
                request.requestId(),
                List.of(new AgentTableRagPort.AgentTableSchemaMatch("experiment_result", 0.91,
                        List.of(Map.of("column", "metric_name", "type", "varchar")))),
                Map.of("provider", "mock-table-rag"),
                "");
        AgentNl2SqlPort sqlPort = request -> new AgentNl2SqlPort.AgentNl2SqlResult(
                true,
                request.requestId(),
                request.query(),
                "use experiment_result",
                "SUCCESS",
                List.of(new AgentNl2SqlPort.AgentSqlCandidate(request.query(),
                        "select avg(metric_value) from experiment_result where metric_name = 'accuracy'")),
                Map.of("provider", "mock-nl2sql"),
                "");
        AgentDataAnalysisPort dataPort = request -> new AgentDataAnalysisPort.AgentDataAnalysisResult(
                true,
                "average accuracy = 92.4",
                "analysis done",
                List.of(),
                Map.of("sampleRows", request.rows()),
                "");
        when(userAccountService.requireUserByToken("Bearer token")).thenReturn(user);
        when(userQuotaService.estimatePreCheckCost("workspace-data")).thenReturn(BigDecimal.valueOf(3));
        when(dataProvider.getIfAvailable()).thenReturn(dataPort);
        when(tableProvider.getIfAvailable()).thenReturn(tablePort);
        when(sqlProvider.getIfAvailable()).thenReturn(sqlPort);
        when(ledgerService.startRun(eq("U1001"), eq("D1001"), eq(""), anyString(), eq("workspace-data"),
                eq("compare experiment metrics"), eq("workspace-data-tools"))).thenReturn(run);
        when(ledgerService.recordToolStart(any(), anyString(), anyString(), eq("workspace/data/run"), anyString()))
                .thenReturn("TOOL_TABLE", "TOOL_SQL", "TOOL_DATA");
        AgentWorkspaceDataService service = new AgentWorkspaceDataService(
                new ObjectMapper(), dataProvider, tableProvider, sqlProvider,
                userAccountService, userQuotaService, repository, ledgerService);
        AgentWorkspaceDataRunRequest request = new AgentWorkspaceDataRunRequest();
        request.setSessionId("D1001");
        request.setQuestion("compare experiment metrics");
        request.setRows(List.of(Map.of("metric_name", "accuracy", "metric_value", 92.4)));
        request.setColumns(List.of("metric_name", "metric_value"));
        request.setModelCodeList(List.of("experiment_result"));

        AgentWorkspaceDataRunResponse response = service.run("Bearer token", request);

        assertEquals("D1001", response.getSessionId());
        assertEquals("RUN1001", response.getRunId());
        assertEquals(3, response.getToolResults().size());
        assertTrue(response.getMissingTools().isEmpty());
        assertEquals(AgentToolOutputNames.TABLE_RAG, response.getToolResults().get(0).getToolName());
        assertEquals(AgentToolOutputNames.NL2SQL, response.getToolResults().get(1).getToolName());
        assertEquals(AgentToolOutputNames.DATA_ANALYSIS, response.getToolResults().get(2).getToolName());
        verify(userQuotaService).estimatePreCheckCost("workspace-data");
        verify(userQuotaService).assertEnoughQuota("U1001", BigDecimal.valueOf(3));
        verify(userQuotaService).consumeForAgentTask(eq("U1001"), eq("D1001"),
                startsWith("workspace-data-DATAREQ"), eq("workspace-data"), any(TokenUsageMetrics.class),
                eq("workspace-data-tools"), anyLong());
        verify(ledgerService).finishRun(eq(run), eq(AgentRun.STATUS_SUCCESS),
                anyString(), eq(""), eq(""), anyLong());
        verify(repository).saveSessionIfAbsent(any(AgentSession.class));
        verify(repository).updateSession(any(AgentSession.class));
    }

    @Test
    void shouldQueryWorkspaceDataHistory() {
        UserAccountService userAccountService = mock(UserAccountService.class);
        AgentRepository repository = mock(AgentRepository.class);
        AgentExecutionLedgerService ledgerService = mock(AgentExecutionLedgerService.class);
        UserAccount user = user("U1001");
        AgentSession session = new AgentSession();
        session.setSessionId("D1001");
        AgentRun run = run("RUN1001");
        run.setSessionId("D1001");
        run.setTaskType("workspace-data");
        run.setQuestion("compare experiment metrics");
        run.setFinalSummary("analysis done");
        run.setStatus(AgentRun.STATUS_SUCCESS);
        run.setStartedAt(LocalDateTime.now());
        when(userAccountService.requireUserByToken("Bearer token")).thenReturn(user);
        when(repository.querySessions("U1001", 10)).thenReturn(List.of(session));
        when(ledgerService.queryRuns("U1001", "D1001", 10)).thenReturn(List.of(run));
        AgentWorkspaceDataService service = new AgentWorkspaceDataService(
                new ObjectMapper(), null, null, null, userAccountService, null, repository, ledgerService);

        AgentWorkspaceDataHistoryResponse response = service.history("Bearer token", "", 10);

        assertEquals(1, response.getTotal());
        assertEquals("RUN1001", response.getItems().getFirst().getRunId());
        assertEquals("analysis done", response.getItems().getFirst().getSummary());
    }

    @Test
    void shouldExposeDefaultDataCatalogForAgentTables() {
        UserAccountService userAccountService = mock(UserAccountService.class);
        when(userAccountService.requireUserByToken("Bearer token")).thenReturn(user("U1001"));
        AgentWorkspaceDataService service = new AgentWorkspaceDataService(
                new ObjectMapper(), null, null, null, userAccountService, null, null, null);

        AgentWorkspaceDataCatalogResponse response = service.catalog("Bearer token");

        assertTrue(response.getDefaultModelCodeList().contains("paper_metadata"));
        assertTrue(response.getDefaultModelCodeList().contains("experiment_result"));
        assertTrue(response.getDefaultModelCodeList().contains("citation_network"));
        assertTrue(response.getDefaultModelCodeList().contains("reading_note"));
        assertEquals(4, response.getModels().size());
        assertEquals("paper_metadata", response.getModels().getFirst().getModelCode());
        assertTrue(response.getSampleQuestions().getFirst().contains("RAG"));
    }

    private UserAccount user(String userId) {
        UserAccount user = new UserAccount();
        user.setUserId(userId);
        user.setUsername("demo");
        return user;
    }

    private AgentRun run(String runId) {
        AgentRun run = new AgentRun();
        run.setRunId(runId);
        return run;
    }
}
















