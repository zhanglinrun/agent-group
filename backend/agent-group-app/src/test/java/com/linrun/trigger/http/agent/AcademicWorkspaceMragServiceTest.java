package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AcademicWorkspaceMragHistoryResponse;
import com.linrun.api.dto.AcademicWorkspaceMragRunRequest;
import com.linrun.api.dto.AcademicWorkspaceMragRunResponse;
import com.linrun.domain.academic.adapter.AcademicAgentRepository;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.service.AcademicExecutionLedgerService;
import com.linrun.domain.academic.model.AcademicSession;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.port.AcademicDeepSearchPort;
import com.linrun.domain.academic.runtime.tool.port.AcademicMultimodalAnalysisPort;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

class AcademicWorkspaceMragServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldRunMragWorkspaceToolsAndRecordLedger() {
        UserAccountService userAccountService = mock(UserAccountService.class);
        UserQuotaService userQuotaService = mock(UserQuotaService.class);
        AcademicAgentRepository repository = mock(AcademicAgentRepository.class);
        AcademicExecutionLedgerService ledgerService = mock(AcademicExecutionLedgerService.class);
        ObjectProvider<AcademicMultimodalAnalysisPort> multimodalProvider = mock(ObjectProvider.class);
        ObjectProvider<AcademicTableRagPort> tableProvider = mock(ObjectProvider.class);
        ObjectProvider<AcademicDeepSearchPort> deepProvider = mock(ObjectProvider.class);
        UserAccount user = user("U1001");
        AcademicAgentRun run = run("RUN1001");
        AcademicMultimodalAnalysisPort multimodalPort = request -> new AcademicMultimodalAnalysisPort.AcademicMultimodalAnalysisResult(
                true,
                "image evidence parsed",
                "the uploaded image contains a paper experiment chart",
                Map.of("provider", "mock-mrag"),
                List.of(),
                "");
        AcademicTableRagPort tablePort = request -> new AcademicTableRagPort.AcademicTableRagResult(
                true,
                request.requestId(),
                List.of(new AcademicTableRagPort.AcademicTableSchemaMatch("experiment_result", 0.88,
                        List.of(Map.of("column", "metric_name")))),
                Map.of("provider", "mock-table-rag"),
                "");
        AcademicDeepSearchPort deepPort = request -> new AcademicDeepSearchPort.AcademicDeepSearchResult(
                true,
                request.query(),
                "external evidence confirms the paper figure metrics",
                "external evidence confirms",
                List.of("paper figure metrics"),
                List.of(new AcademicDeepSearchPort.AcademicDeepSearchDocument(
                        "paper figure", "https://example.com/figure", "figure content", "mock")),
                List.of(),
                Map.of("provider", "mock-deep-search"),
                "");
        when(userAccountService.requireUserByToken("Bearer token")).thenReturn(user);
        when(userQuotaService.estimatePreCheckCost("workspace-mrag")).thenReturn(BigDecimal.valueOf(3));
        when(multimodalProvider.getIfAvailable()).thenReturn(multimodalPort);
        when(tableProvider.getIfAvailable()).thenReturn(tablePort);
        when(deepProvider.getIfAvailable()).thenReturn(deepPort);
        when(ledgerService.startRun(eq("U1001"), eq("M1001"), eq(""), anyString(), eq("workspace-mrag"),
                eq("cross check paper figures"), eq("workspace-mrag-tools"))).thenReturn(run);
        when(ledgerService.recordToolStart(any(), anyString(), anyString(), eq("workspace/mrag/run"), anyString()))
                .thenAnswer(invocation -> "TOOL_" + invocation.getArgument(2, String.class));
        AcademicWorkspaceMragService service = new AcademicWorkspaceMragService(
                new ObjectMapper(), multimodalProvider, tableProvider, deepProvider,
                userAccountService, userQuotaService, repository, ledgerService);
        AcademicWorkspaceMragRunRequest request = new AcademicWorkspaceMragRunRequest();
        request.setSessionId("M1001");
        request.setQuestion("cross check paper figures");
        request.setImageUrls(List.of("https://example.com/figure.png"));
        request.setModelCodeList(List.of("experiment_result"));

        AcademicWorkspaceMragRunResponse response = service.run("Bearer token", request);

        assertEquals("M1001", response.getSessionId());
        assertEquals("RUN1001", response.getRunId());
        assertEquals(3, response.getToolResults().size());
        assertTrue(response.getMissingTools().isEmpty());
        assertEquals(AcademicToolOutputNames.MULTIMODAL_AGENT, response.getToolResults().get(0).getToolName());
        assertEquals(AcademicToolOutputNames.TABLE_RAG, response.getToolResults().get(1).getToolName());
        assertEquals(AcademicToolOutputNames.DEEP_SEARCH, response.getToolResults().get(2).getToolName());
        verify(userQuotaService).estimatePreCheckCost("workspace-mrag");
        verify(userQuotaService).assertEnoughQuota("U1001", BigDecimal.valueOf(3));
        verify(userQuotaService).consumeForAcademicTask(eq("U1001"), eq("M1001"),
                startsWith("workspace-mrag-MRAGREQ"), eq("workspace-mrag"), any(GuideTokenUsage.class),
                eq("workspace-mrag-tools"), anyLong());
        verify(ledgerService).finishRun(eq(run), eq(AcademicAgentRun.STATUS_SUCCESS),
                anyString(), eq(""), eq(""), anyLong());
        verify(repository).saveSessionIfAbsent(any(AcademicSession.class));
        verify(repository).updateSession(any(AcademicSession.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRunMragWorkspaceToolsConcurrentlyAndKeepResultOrder() {
        UserAccountService userAccountService = mock(UserAccountService.class);
        UserQuotaService userQuotaService = mock(UserQuotaService.class);
        AcademicAgentRepository repository = mock(AcademicAgentRepository.class);
        AcademicExecutionLedgerService ledgerService = mock(AcademicExecutionLedgerService.class);
        ObjectProvider<AcademicMultimodalAnalysisPort> multimodalProvider = mock(ObjectProvider.class);
        ObjectProvider<AcademicTableRagPort> tableProvider = mock(ObjectProvider.class);
        ObjectProvider<AcademicDeepSearchPort> deepProvider = mock(ObjectProvider.class);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch started = new CountDownLatch(3);
        UserAccount user = user("U1001");
        AcademicAgentRun run = run("RUN1001");
        AcademicMultimodalAnalysisPort multimodalPort = request -> {
            awaitAll(started);
            return new AcademicMultimodalAnalysisPort.AcademicMultimodalAnalysisResult(
                    true, "image evidence parsed", "image content", Map.of(), List.of(), "");
        };
        AcademicTableRagPort tablePort = request -> {
            awaitAll(started);
            return new AcademicTableRagPort.AcademicTableRagResult(
                    true, request.requestId(), List.of(), Map.of(), "");
        };
        AcademicDeepSearchPort deepPort = request -> {
            awaitAll(started);
            return new AcademicDeepSearchPort.AcademicDeepSearchResult(
                    true, request.query(), "search summary", "search answer",
                    List.of(), List.of(), List.of(), Map.of(), "");
        };
        when(userAccountService.requireUserByToken("Bearer token")).thenReturn(user);
        when(userQuotaService.estimatePreCheckCost("workspace-mrag")).thenReturn(BigDecimal.valueOf(3));
        when(multimodalProvider.getIfAvailable()).thenReturn(multimodalPort);
        when(tableProvider.getIfAvailable()).thenReturn(tablePort);
        when(deepProvider.getIfAvailable()).thenReturn(deepPort);
        when(ledgerService.startRun(eq("U1001"), eq("M1001"), eq(""), anyString(), eq("workspace-mrag"),
                eq("cross check paper figures"), eq("workspace-mrag-tools"))).thenReturn(run);
        when(ledgerService.recordToolStart(any(), anyString(), anyString(), eq("workspace/mrag/run"), anyString()))
                .thenAnswer(invocation -> "TOOL_" + invocation.getArgument(2, String.class));
        AcademicWorkspaceMragService service = new AcademicWorkspaceMragService(
                new ObjectMapper(), multimodalProvider, tableProvider, deepProvider,
                userAccountService, userQuotaService, repository, ledgerService, executor);
        AcademicWorkspaceMragRunRequest request = new AcademicWorkspaceMragRunRequest();
        request.setSessionId("M1001");
        request.setQuestion("cross check paper figures");

        try {
            AcademicWorkspaceMragRunResponse response = service.run("Bearer token", request);

            assertEquals(3, response.getToolResults().size());
            assertEquals(AcademicToolOutputNames.MULTIMODAL_AGENT, response.getToolResults().get(0).getToolName());
            assertEquals(AcademicToolOutputNames.TABLE_RAG, response.getToolResults().get(1).getToolName());
            assertEquals(AcademicToolOutputNames.DEEP_SEARCH, response.getToolResults().get(2).getToolName());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldQueryWorkspaceMragHistory() {
        UserAccountService userAccountService = mock(UserAccountService.class);
        AcademicAgentRepository repository = mock(AcademicAgentRepository.class);
        AcademicExecutionLedgerService ledgerService = mock(AcademicExecutionLedgerService.class);
        UserAccount user = user("U1001");
        AcademicSession session = new AcademicSession();
        session.setSessionId("M1001");
        AcademicAgentRun run = run("RUN1001");
        run.setSessionId("M1001");
        run.setTaskType("workspace-mrag");
        run.setQuestion("cross check paper figures");
        run.setFinalSummary("mrag done");
        run.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        run.setStartedAt(LocalDateTime.now());
        when(userAccountService.requireUserByToken("Bearer token")).thenReturn(user);
        when(repository.querySessions("U1001", 10)).thenReturn(List.of(session));
        when(ledgerService.queryRuns("U1001", "M1001", 10)).thenReturn(List.of(run));
        AcademicWorkspaceMragService service = new AcademicWorkspaceMragService(
                new ObjectMapper(), null, null, null, userAccountService, null, repository, ledgerService);

        AcademicWorkspaceMragHistoryResponse response = service.history("Bearer token", "", 10);

        assertEquals(1, response.getTotal());
        assertEquals("RUN1001", response.getItems().getFirst().getRunId());
        assertEquals("mrag done", response.getItems().getFirst().getSummary());
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

    private void awaitAll(CountDownLatch started) {
        started.countDown();
        try {
            assertTrue(started.await(1, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
