package com.linrun.domain.academic.runtime.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.service.AcademicExecutionLedgerService;
import com.linrun.domain.academic.ledger.service.AcademicLedgerContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcademicToolBatchExecutorTest {

    @Test
    void shouldExecuteBatchAndRecordLedger() {
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.register(definition("quota_status"), command -> Map.of("quota", 100));
        registry.register(definition("report_tool"), command -> Map.of(
                "summary", "report generated",
                "fileRefs", List.of(Map.of(
                        "artifactId", "A1001",
                        "fileName", "report.md",
                        "downloadUrl", "/files/report.md"))));
        registry.register(definition("unstable_tool"), command -> {
            throw new IllegalStateException("boom");
        });
        AcademicToolCollection collection = new AcademicToolCollectionFactory(registry).buildAll("deep_research");
        AcademicExecutionLedgerService ledgerService = mock(AcademicExecutionLedgerService.class);
        when(ledgerService.recordToolStart(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn("INV1", "INV2", "INV3");
        AcademicToolBatchExecutor executor = new AcademicToolBatchExecutor(new ObjectMapper(), ledgerService);
        AcademicLedgerContext.Context context =
                new AcademicLedgerContext.Context("RUN1", "REQ1", "S1", "U1", "deep_research");

        List<AcademicToolCallResult> results = executor.executeAll(collection, List.of(
                AcademicToolCallCommand.builder("quota_status").arguments(Map.of("userId", "U1")).build(),
                AcademicToolCallCommand.builder("report_tool").build(),
                AcademicToolCallCommand.builder("unstable_tool").build()), Runnable::run, context);

        assertTrue(results.getFirst().isSuccess());
        assertEquals(100, results.getFirst().getResult().get("quota"));
        assertTrue(results.get(1).isSuccess());
        assertFalse(results.get(2).isSuccess());
        assertEquals("TOOL_EXECUTE_FAILED", results.get(2).getErrorCode());
        verify(ledgerService, times(3)).recordToolStart(any(), anyString(), anyString(), anyString(), anyString());
        verify(ledgerService).recordToolFinish(eq("INV1"), eq(AcademicAgentRun.STATUS_SUCCESS),
                anyString(), anyString(), anyInt(), anyString(), anyLong());
        verify(ledgerService).recordToolFinish(eq("INV2"), eq(AcademicAgentRun.STATUS_SUCCESS),
                anyString(), anyString(), anyInt(), anyString(), anyLong());
        verify(ledgerService).recordToolFinish(eq("INV3"), eq(AcademicAgentRun.STATUS_FAILED),
                anyString(), anyString(), anyInt(), anyString(), anyLong());
        verify(ledgerService).recordToolArtifacts(eq(context), eq("INV2"), eq("report_tool"), any());
    }

    @Test
    void shouldReturnBusyResultWhenExecutorRejects() {
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.register(definition("quota_status"), command -> Map.of("quota", 100));
        AcademicToolCollection collection = new AcademicToolCollectionFactory(registry).buildAll("deep_research");
        Executor rejected = command -> {
            throw new RejectedExecutionException("full");
        };

        List<AcademicToolCallResult> results = new AcademicToolBatchExecutor(new ObjectMapper(), null)
                .executeAll(collection, List.of(AcademicToolCallCommand.builder("quota_status").build()),
                        rejected, null);

        assertFalse(results.getFirst().isSuccess());
        assertEquals("AGENT_BUSY", results.getFirst().getErrorCode());
    }

    private AcademicToolDefinition definition(String name) {
        return AcademicToolDefinition.builder(name)
                .description("test tool")
                .category("test")
                .source("unit")
                .inputSchema(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                .requiredArguments(List.of())
                .enabled(true)
                .build();
    }
}















