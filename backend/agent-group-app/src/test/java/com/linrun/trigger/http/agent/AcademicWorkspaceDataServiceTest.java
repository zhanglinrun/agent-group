package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AcademicWorkspaceDataCatalogResponse;
import com.linrun.api.dto.AcademicWorkspaceDataHistoryResponse;
import com.linrun.api.dto.AcademicWorkspaceDataRunRequest;
import com.linrun.api.dto.AcademicWorkspaceDataRunResponse;
import com.linrun.domain.academic.adapter.AcademicAgentRepository;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.service.AcademicExecutionLedgerService;
import com.linrun.domain.academic.model.AcademicSession;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.port.AcademicDataAnalysisPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicNl2SqlPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicTableRagPort;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.agent.conversation.model.GuideTokenUsage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcademicWorkspaceDataServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldRunDataWorkspaceToolsAndRecordLedger() {
        UserAccountService userAccountService = mock(UserAccountService.class);
        UserQuotaService userQuotaService = mock(UserQuotaService.class);
        AcademicAgentRepository repository = mock(AcademicAgentRepository.class);
        AcademicExecutionLedgerService ledgerService = mock(AcademicExecutionLedgerService.class);
        ObjectProvider<AcademicDataAnalysisPort> dataProvider = mock(ObjectProvider.class);
        ObjectProvider<AcademicTableRagPort> tableProvider = mock(ObjectProvider.class);
        ObjectProvider<AcademicNl2SqlPort> sqlProvider = mock(ObjectProvider.class);
        UserAccount user = user("U1001");
        AcademicAgentRun run = run("RUN1001");
        AcademicTableRagPort tablePort = request -> new AcademicTableRagPort.AcademicTableRagResult(
                true,
                request.requestId(),
                List.of(new AcademicTableRagPort.AcademicTableSchemaMatch("trade_order", 0.91,
                        List.of(Map.of("column", "pay_status", "type", "varchar")))),
                Map.of("provider", "mock-table-rag"),
                "");
        AcademicNl2SqlPort sqlPort = request -> new AcademicNl2SqlPort.AcademicNl2SqlResult(
                true,
                request.requestId(),
                request.query(),
                "use trade_order",
                "SUCCESS",
                List.of(new AcademicNl2SqlPort.AcademicSqlCandidate(request.query(),
                        "select count(*) from trade_order where pay_status = 'PAY_SUCCESS'")),
                Map.of("provider", "mock-nl2sql"),
                "");
        AcademicDataAnalysisPort dataPort = request -> new AcademicDataAnalysisPort.AcademicDataAnalysisResult(
                true,
                "pay success count = 12",
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
                eq("count paid orders"), eq("workspace-data-tools"))).thenReturn(run);
        when(ledgerService.recordToolStart(any(), anyString(), anyString(), eq("workspace/data/run"), anyString()))
                .thenReturn("TOOL_TABLE", "TOOL_SQL", "TOOL_DATA");
        AcademicWorkspaceDataService service = new AcademicWorkspaceDataService(
                new ObjectMapper(), dataProvider, tableProvider, sqlProvider,
                userAccountService, userQuotaService, repository, ledgerService);
        AcademicWorkspaceDataRunRequest request = new AcademicWorkspaceDataRunRequest();
        request.setSessionId("D1001");
        request.setQuestion("count paid orders");
        request.setRows(List.of(Map.of("pay_status", "PAY_SUCCESS", "count", 12)));
        request.setColumns(List.of("pay_status", "count"));
        request.setModelCodeList(List.of("trade_order"));

        AcademicWorkspaceDataRunResponse response = service.run("Bearer token", request);

        assertEquals("D1001", response.getSessionId());
        assertEquals("RUN1001", response.getRunId());
        assertEquals(3, response.getToolResults().size());
        assertTrue(response.getMissingTools().isEmpty());
        assertEquals(AcademicToolOutputNames.TABLE_RAG, response.getToolResults().get(0).getToolName());
        assertEquals(AcademicToolOutputNames.NL2SQL, response.getToolResults().get(1).getToolName());
        assertEquals(AcademicToolOutputNames.DATA_ANALYSIS, response.getToolResults().get(2).getToolName());
        verify(userQuotaService).estimatePreCheckCost("workspace-data");
        verify(userQuotaService).assertEnoughQuota("U1001", BigDecimal.valueOf(3));
        verify(userQuotaService).consumeForAcademicTask(eq("U1001"), eq("D1001"),
                startsWith("workspace-data-DATAREQ"), eq("workspace-data"), any(GuideTokenUsage.class),
                eq("workspace-data-tools"), anyLong());
        verify(ledgerService).finishRun(eq(run), eq(AcademicAgentRun.STATUS_SUCCESS),
                anyString(), eq(""), eq(""), anyLong());
        verify(repository).saveSessionIfAbsent(any(AcademicSession.class));
        verify(repository).updateSession(any(AcademicSession.class));
    }

    @Test
    void shouldQueryWorkspaceDataHistory() {
        UserAccountService userAccountService = mock(UserAccountService.class);
        AcademicAgentRepository repository = mock(AcademicAgentRepository.class);
        AcademicExecutionLedgerService ledgerService = mock(AcademicExecutionLedgerService.class);
        UserAccount user = user("U1001");
        AcademicSession session = new AcademicSession();
        session.setSessionId("D1001");
        AcademicAgentRun run = run("RUN1001");
        run.setSessionId("D1001");
        run.setTaskType("workspace-data");
        run.setQuestion("count paid orders");
        run.setFinalSummary("analysis done");
        run.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        run.setStartedAt(LocalDateTime.now());
        when(userAccountService.requireUserByToken("Bearer token")).thenReturn(user);
        when(repository.querySessions("U1001", 10)).thenReturn(List.of(session));
        when(ledgerService.queryRuns("U1001", "D1001", 10)).thenReturn(List.of(run));
        AcademicWorkspaceDataService service = new AcademicWorkspaceDataService(
                new ObjectMapper(), null, null, null, userAccountService, null, repository, ledgerService);

        AcademicWorkspaceDataHistoryResponse response = service.history("Bearer token", "", 10);

        assertEquals(1, response.getTotal());
        assertEquals("RUN1001", response.getItems().getFirst().getRunId());
        assertEquals("analysis done", response.getItems().getFirst().getSummary());
    }

    @Test
    void shouldExposeDefaultDataCatalogForTradeAndQuotaTables() {
        UserAccountService userAccountService = mock(UserAccountService.class);
        when(userAccountService.requireUserByToken("Bearer token")).thenReturn(user("U1001"));
        AcademicWorkspaceDataService service = new AcademicWorkspaceDataService(
                new ObjectMapper(), null, null, null, userAccountService, null, null, null);

        AcademicWorkspaceDataCatalogResponse response = service.catalog("Bearer token");

        assertTrue(response.getDefaultModelCodeList().contains("trade_order"));
        assertTrue(response.getDefaultModelCodeList().contains("group_buy_order_lock"));
        assertTrue(response.getDefaultModelCodeList().contains("user_quota_account"));
        assertTrue(response.getDefaultModelCodeList().contains("user_quota_flow"));
        assertEquals(4, response.getModels().size());
        assertEquals("trade_order", response.getModels().getFirst().getModelCode());
        assertTrue(response.getSampleQuestions().getFirst().contains("拼团订单"));
    }

    private UserAccount user(String userId) {
        UserAccount user = new UserAccount();
        user.setUserId(userId);
        user.setUsername("demo");
        return user;
    }

    private AcademicAgentRun run(String runId) {
        AcademicAgentRun run = new AcademicAgentRun();
        run.setRunId(runId);
        return run;
    }
}
