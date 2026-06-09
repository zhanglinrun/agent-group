package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AcademicRunDetailResponse;
import com.linrun.api.dto.AcademicWorkspaceImageGenerateRequest;
import com.linrun.api.dto.AcademicWorkspaceImageGenerateResponse;
import com.linrun.api.dto.AcademicWorkspaceImageHistoryResponse;
import com.linrun.domain.academic.adapter.AcademicAgentRepository;
import com.linrun.domain.academic.ledger.model.AcademicAgentRun;
import com.linrun.domain.academic.ledger.service.AcademicExecutionLedgerService;
import com.linrun.domain.academic.model.AcademicArtifact;
import com.linrun.domain.academic.model.AcademicFile;
import com.linrun.domain.academic.model.AcademicSession;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import com.linrun.domain.academic.runtime.tool.port.AcademicImageGenerationPort;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.model.UserModelConfig;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.account.service.UserQuotaService;
import com.linrun.domain.agent.conversation.model.TokenUsageMetrics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcademicWorkspaceImageServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldGenerateImageThroughToolRuntimeAndPersistArtifact() {
        UserAccountService userAccountService = mock(UserAccountService.class);
        UserQuotaService userQuotaService = mock(UserQuotaService.class);
        AcademicAgentRepository repository = mock(AcademicAgentRepository.class);
        AcademicExecutionLedgerService ledgerService = mock(AcademicExecutionLedgerService.class);
        ObjectProvider<AcademicImageGenerationPort> provider = mock(ObjectProvider.class);
        UserAccount user = user("U1001");
        AcademicAgentRun run = run("RUN1001");
        AcademicFile sourceFile = new AcademicFile();
        sourceFile.setFileId("AF1001");
        sourceFile.setObjectUrl("/objects/source.png");
        UserModelConfig modelConfig = new UserModelConfig();
        modelConfig.setEnabled(true);
        modelConfig.setImageBaseUrl("https://image.example.com/v1");
        modelConfig.setImageApiKey("sk-image-secret-5678");
        modelConfig.setImageModel("custom-image-model");
        AtomicReference<AcademicImageGenerationPort.AcademicImageGenerationRequest> capturedRequest = new AtomicReference<>();
        AcademicImageGenerationPort port = imageRequest -> {
            capturedRequest.set(imageRequest);
            return new AcademicImageGenerationPort.AcademicImageGenerationResult(
                true,
                "mock-image",
                "已生成拼团活动主�?,
                false,
                List.of(AcademicToolFileRef.builder()
                        .artifactId("IMG-1")
                        .fileName("poster.png")
                        .downloadUrl("/api/v1/academic/artifacts/download?sessionId=S1001&artifactId=IMG-1")
                        .previewUrl("/files/poster.png")
                        .contentType("image/png")
                        .fileSize(2048L)
                        .build()),
                "");
        };
        when(userAccountService.requireUserByToken("Bearer token")).thenReturn(user);
        when(userQuotaService.estimatePreCheckCost("workspace-image")).thenReturn(BigDecimal.valueOf(4));
        when(userQuotaService.queryRuntimeModelConfig("U1001")).thenReturn(Optional.of(modelConfig));
        when(provider.getIfAvailable()).thenReturn(port);
        when(repository.queryFile("U1001", "AF1001")).thenReturn(Optional.of(sourceFile));
        when(ledgerService.startRun(eq("U1001"), eq("S1001"), eq(""), anyString(), eq("workspace-image"),
                eq("生成拼团活动主图"), eq(AcademicToolOutputNames.IMAGE_GENERATION))).thenReturn(run);
        when(ledgerService.recordToolStart(any(), anyString(), eq(AcademicToolOutputNames.IMAGE_GENERATION),
                eq("workspace/image/generate"), anyString())).thenReturn("TOOL1001");
        AcademicWorkspaceImageService service = new AcademicWorkspaceImageService(
                new ObjectMapper(), provider, userAccountService, userQuotaService, repository, ledgerService);
        AcademicWorkspaceImageGenerateRequest request = new AcademicWorkspaceImageGenerateRequest();
        request.setSessionId("S1001");
        request.setPrompt("生成拼团活动主图");
        request.setSize("1024x1024");
        request.setSourceFileIds(List.of("AF1001"));

        AcademicWorkspaceImageGenerateResponse response = service.generate("Bearer token", request);

        assertEquals("S1001", response.getSessionId());
        assertEquals("RUN1001", response.getRunId());
        assertEquals("TOOL1001", response.getInvocationId());
        assertEquals("mock-image", response.getProvider());
        assertEquals(List.of("/objects/source.png"), capturedRequest.get().sourceImageUrls());
        assertEquals("custom-image-model", capturedRequest.get().model());
        assertEquals("https://image.example.com/v1", capturedRequest.get().baseUrl());
        assertEquals("sk-image-secret-5678", capturedRequest.get().apiKey());
        assertEquals("auto", capturedRequest.get().quality());
        assertEquals("1:1", capturedRequest.get().aspectRatio());
        assertEquals("poster.png", response.getFileRefs().getFirst().getFileName());
        assertEquals("IMG-1", response.getArtifactRefs().getFirst().getArtifactId());
        ArgumentCaptor<AcademicArtifact> artifactCaptor = ArgumentCaptor.forClass(AcademicArtifact.class);
        verify(repository).saveArtifact(artifactCaptor.capture());
        assertEquals("IMG-1", artifactCaptor.getValue().getArtifactId());
        assertEquals("TOOL1001", artifactCaptor.getValue().getToolInvocationId());
        ArgumentCaptor<String> argumentsCaptor = ArgumentCaptor.forClass(String.class);
        verify(ledgerService).recordToolStart(any(), anyString(), eq(AcademicToolOutputNames.IMAGE_GENERATION),
                eq("workspace/image/generate"), argumentsCaptor.capture());
        assertFalse(argumentsCaptor.getValue().contains("sk-image-secret-5678"));
        assertFalse(argumentsCaptor.getValue().contains("imageBaseUrl"));
        verify(userQuotaService).estimatePreCheckCost("workspace-image");
        verify(userQuotaService).assertEnoughQuota("U1001", BigDecimal.valueOf(4));
        verify(userQuotaService).consumeForAcademicTask(eq("U1001"), eq("S1001"),
                startsWith("workspace-image-IMGREQ"), eq("workspace-image"), any(TokenUsageMetrics.class),
                eq("workspace-image-tool"), anyLong());
        verify(ledgerService).recordToolFinish(eq("TOOL1001"), eq(AcademicAgentRun.STATUS_SUCCESS),
                eq("已生成拼团活动主�?), anyString(), eq(0), eq(""), anyLong());
        verify(ledgerService).finishRun(eq(run), eq(AcademicAgentRun.STATUS_SUCCESS),
                eq("已生成拼团活动主�?), eq(""), eq(""), anyLong());
    }

    @Test
    void shouldQueryImageHistoryFromLatestSessions() {
        UserAccountService userAccountService = mock(UserAccountService.class);
        AcademicAgentRepository repository = mock(AcademicAgentRepository.class);
        AcademicExecutionLedgerService ledgerService = mock(AcademicExecutionLedgerService.class);
        UserAccount user = user("U1001");
        AcademicSession session = new AcademicSession();
        session.setSessionId("S1001");
        AcademicArtifact artifact = new AcademicArtifact();
        artifact.setArtifactId("IMG-1");
        artifact.setSessionId("S1001");
        artifact.setRunId("RUN1001");
        artifact.setToolInvocationId("TOOL1001");
        artifact.setArtifactType("IMAGE");
        artifact.setTitle("poster.png");
        artifact.setContent("/files/poster.png");
        artifact.setDownloadUrl("/files/poster.png");
        artifact.setCreateTime(LocalDateTime.now());
        AcademicAgentRun run = run("RUN1001");
        run.setSessionId("S1001");
        run.setRequestId("IMGREQ1001");
        run.setTaskType("workspace-image");
        run.setQuestion("编辑拼团活动主图");
        run.setFinalSummary("已生�?3 张图�?);
        run.setStatus(AcademicAgentRun.STATUS_SUCCESS);
        AcademicRunDetailResponse detail = new AcademicRunDetailResponse();
        AcademicRunDetailResponse.ToolInvocation invocation = new AcademicRunDetailResponse.ToolInvocation();
        invocation.setToolName(AcademicToolOutputNames.IMAGE_GENERATION);
        invocation.setAction("workspace/image/generate");
        invocation.setArgumentsJson("""
                {"mode":"edit","model":"gpt-image-2","quality":"high","aspectRatio":"3:2","size":"1536x1024","batchCount":3,"sourceImageUrls":["/objects/source-a.png","/objects/source-b.png"]}
                """);
        detail.setToolInvocations(List.of(invocation));
        when(userAccountService.requireUserByToken("Bearer token")).thenReturn(user);
        when(repository.querySessions("U1001", 10)).thenReturn(List.of(session));
        when(repository.queryArtifacts("U1001", "S1001")).thenReturn(List.of(artifact));
        when(ledgerService.queryRuns("U1001", "S1001", 10)).thenReturn(List.of(run));
        when(ledgerService.queryRunDetail("U1001", "RUN1001")).thenReturn(detail);
        AcademicWorkspaceImageService service = new AcademicWorkspaceImageService(
                new ObjectMapper(), null, userAccountService, null, repository, ledgerService);

        AcademicWorkspaceImageHistoryResponse response = service.history("Bearer token", "", 10);

        assertEquals(1, response.getTotal());
        assertFalse(response.getItems().isEmpty());
        assertEquals("IMG-1", response.getItems().getFirst().getArtifactId());
        assertEquals(1, response.getBatchTotal());
        assertEquals("IMG-1", response.getBatches().getFirst().getImages().getFirst().getArtifactId());
        assertEquals("IMGREQ1001", response.getBatches().getFirst().getRequestId());
        assertEquals("edit", response.getBatches().getFirst().getMode());
        assertEquals("gpt-image-2", response.getBatches().getFirst().getModel());
        assertEquals("high", response.getBatches().getFirst().getQuality());
        assertEquals("3:2", response.getBatches().getFirst().getAspectRatio());
        assertEquals("1536x1024", response.getBatches().getFirst().getSize());
        assertEquals(3, response.getBatches().getFirst().getBatchCount());
        assertEquals(2, response.getBatches().getFirst().getSourceImageCount());
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
















