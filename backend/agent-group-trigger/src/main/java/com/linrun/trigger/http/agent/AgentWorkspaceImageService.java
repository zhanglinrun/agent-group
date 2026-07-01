package com.linrun.trigger.http.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.api.dto.AgentRunDetailResponse;
import com.linrun.api.dto.AgentWorkspaceImageGenerateRequest;
import com.linrun.api.dto.AgentWorkspaceImageGenerateResponse;
import com.linrun.api.dto.AgentWorkspaceImageHistoryResponse;
import com.linrun.domain.agent.adapter.AgentRepository;
import com.linrun.domain.agent.ledger.model.AgentRun;
import com.linrun.domain.agent.ledger.service.AgentExecutionLedgerService;
import com.linrun.domain.agent.ledger.service.AgentLedgerContext;
import com.linrun.domain.agent.model.AgentArtifact;
import com.linrun.domain.agent.model.AgentFile;
import com.linrun.domain.agent.model.AgentSession;
import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.common.AgentImageGenerationToolRuntime;
import com.linrun.domain.agent.runtime.tool.output.AgentToolFileRef;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputProjector;
import com.linrun.domain.agent.runtime.tool.output.AgentToolStructuredOutput;
import com.linrun.domain.agent.runtime.tool.port.AgentImageGenerationPort;
import com.linrun.domain.agent.runtime.security.PromptInjectionGuard;
import com.linrun.domain.account.model.UserAccount;
import com.linrun.domain.account.model.UserModelConfig;
import com.linrun.domain.account.service.UserAccountService;
import com.linrun.domain.quota.service.UserQuotaService;
import com.linrun.domain.quota.model.TokenUsageMetrics;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AgentWorkspaceImageService {

    private static final String TASK_TYPE = "workspace-image";
    private static final String ACTION = "workspace/image/generate";

    private final ObjectMapper objectMapper;
    private final ObjectProvider<AgentImageGenerationPort> imageGenerationPort;
    private final UserAccountService userAccountService;
    private final UserQuotaService userQuotaService;
    private final AgentRepository agentRepository;
    private final AgentExecutionLedgerService ledgerService;

    public AgentWorkspaceImageService(ObjectMapper objectMapper,
                                         ObjectProvider<AgentImageGenerationPort> imageGenerationPort,
                                         UserAccountService userAccountService,
                                         UserQuotaService userQuotaService,
                                         AgentRepository agentRepository,
                                         AgentExecutionLedgerService ledgerService) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.imageGenerationPort = imageGenerationPort;
        this.userAccountService = userAccountService;
        this.userQuotaService = userQuotaService;
        this.agentRepository = agentRepository;
        this.ledgerService = ledgerService;
    }

    public AgentWorkspaceImageGenerateResponse generate(String token,
                                                           AgentWorkspaceImageGenerateRequest request) {
        AgentWorkspaceImageGenerateRequest safeRequest = request == null
                ? new AgentWorkspaceImageGenerateRequest()
                : request;
        if (!StringUtils.hasText(safeRequest.getPrompt())) {
            throw new AppException("IMAGE_WORKSPACE_0001", "图像生成提示词不能为空");
        }
        safeRequest.setPrompt(PromptInjectionGuard.sanitize(safeRequest.getPrompt()));
        UserAccount user = userAccountService.requireUserByToken(token);
        String userId = user.getUserId();
        preCheckQuota(userId);
        String sessionId = firstText(safeRequest.getSessionId(), "IMG" + System.currentTimeMillis());
        String requestId = "IMGREQ" + UUID.randomUUID().toString().replace("-", "");
        saveSession(userId, sessionId, safeRequest.getPrompt());

        AgentRun run = ledgerService.startRun(
                userId, sessionId, "", requestId, TASK_TYPE, safeRequest.getPrompt(), AgentToolOutputNames.IMAGE_GENERATION);
        AgentLedgerContext.Context context = new AgentLedgerContext.Context(
                run.getRunId(), requestId, sessionId, userId, TASK_TYPE);
        UserModelConfig runtimeModelConfig = runtimeModelConfig(userId);
        Map<String, Object> arguments = arguments(userId, safeRequest, runtimeModelConfig);
        String invocationId = ledgerService.recordToolStart(context,
                "workspace-image-" + requestId,
                AgentToolOutputNames.IMAGE_GENERATION,
                ACTION,
                json(arguments));
        long startedAt = System.currentTimeMillis();
        try {
            AgentImageGenerationPort port = imageGenerationPort == null ? null : imageGenerationPort.getIfAvailable();
            if (port == null) {
                throw new AppException("IMAGE_WORKSPACE_0002", "后端绘图模型异常，请检查图像模型配置后重试");
            }
            AgentToolStructuredOutput output = new AgentImageGenerationToolRuntime(
                    port,
                    runtimeModelConfig == null ? "" : runtimeModelConfig.getImageBaseUrl(),
                    runtimeModelConfig == null ? "" : runtimeModelConfig.getImageApiKey())
                    .call(AgentToolCallCommand.builder(AgentToolOutputNames.IMAGE_GENERATION)
                            .action(ACTION)
                            .requestId(requestId)
                            .sessionId(sessionId)
                            .userId(userId)
                            .runId(run.getRunId())
                            .arguments(arguments)
                            .build());
            Map<String, Object> result = AgentToolOutputProjector.toResultMap(output);
            long durationMillis = elapsed(startedAt);
            consumeQuota(userId, sessionId, requestId, durationMillis);
            List<AgentWorkspaceImageGenerateResponse.ArtifactRef> artifacts =
                    saveArtifacts(userId, sessionId, run.getRunId(), invocationId, output.getFileRefs());
            ledgerService.recordToolFinish(invocationId, AgentRun.STATUS_SUCCESS,
                    output.getSummary(), json(result), 0, "", durationMillis);
            ledgerService.finishRun(run, AgentRun.STATUS_SUCCESS,
                    output.getSummary(), "", "", durationMillis);
            updateSession(userId, sessionId, safeRequest.getPrompt(), output.getSummary());
            return response(requestId, sessionId, run.getRunId(), invocationId, output, artifacts);
        } catch (AppException e) {
            recordFailure(run, invocationId, e.getCode(), e.getMessage(), startedAt);
            throw e;
        } catch (Exception e) {
            recordFailure(run, invocationId, "IMAGE_WORKSPACE_0003", e.getMessage(), startedAt);
            throw new AppException("IMAGE_WORKSPACE_0003", "后端绘图模型异常，请检查图像模型配置后重试");
        }
    }

    private void preCheckQuota(String userId) {
        userQuotaService.assertEnoughQuota(userId, userQuotaService.estimatePreCheckCost(TASK_TYPE));
    }

    private void consumeQuota(String userId, String sessionId, String requestId, long durationMillis) {
        userQuotaService.consumeForAgentTask(userId, sessionId, TASK_TYPE + "-" + requestId, TASK_TYPE,
                TokenUsageMetrics.empty(), TASK_TYPE + "-tool", durationMillis);
    }

    private UserModelConfig runtimeModelConfig(String userId) {
        if (userQuotaService == null) {
            return null;
        }
        return userQuotaService.queryRuntimeModelConfig(userId).orElse(null);
    }

    public AgentWorkspaceImageHistoryResponse history(String token, String sessionId, int limit) {
        UserAccount user = userAccountService.requireUserByToken(token);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        List<AgentSession> sessions = historySessions(user.getUserId(), sessionId, safeLimit);
        List<AgentArtifact> artifacts = new ArrayList<>();
        List<AgentRun> runs = new ArrayList<>();
        for (AgentSession session : sessions) {
            String currentSessionId = firstText(session.getSessionId());
            if (!StringUtils.hasText(currentSessionId)) {
                continue;
            }
            artifacts.addAll(safeArtifacts(agentRepository.queryArtifacts(user.getUserId(), currentSessionId)));
            runs.addAll(safeRuns(ledgerService == null
                    ? List.of()
                    : ledgerService.queryRuns(user.getUserId(), currentSessionId, safeLimit)));
            if (artifacts.size() >= safeLimit && runs.size() >= safeLimit) {
                break;
            }
        }
        List<AgentWorkspaceImageGenerateResponse.ArtifactRef> items = artifacts.stream()
                .filter(this::isImageArtifact)
                .limit(safeLimit)
                .map(this::artifactRef)
                .toList();
        List<AgentWorkspaceImageHistoryResponse.Batch> batches = historyBatches(user.getUserId(), runs, artifacts, safeLimit);
        AgentWorkspaceImageHistoryResponse response = new AgentWorkspaceImageHistoryResponse();
        response.setSessionId(firstText(sessionId));
        response.setTotal(items.size());
        response.setItems(items);
        response.setBatchTotal(batches.size());
        response.setBatches(batches);
        return response;
    }

    private List<AgentSession> historySessions(String userId, String sessionId, int safeLimit) {
        if (StringUtils.hasText(sessionId)) {
            AgentSession session = new AgentSession();
            session.setSessionId(sessionId.trim());
            return List.of(session);
        }
        List<AgentSession> sessions = agentRepository.querySessions(userId, safeLimit);
        return sessions == null ? List.of() : sessions;
    }

    private List<AgentArtifact> safeArtifacts(List<AgentArtifact> artifacts) {
        return artifacts == null ? List.of() : artifacts;
    }

    private List<AgentRun> safeRuns(List<AgentRun> runs) {
        return runs == null ? List.of() : runs;
    }

    private List<AgentWorkspaceImageHistoryResponse.Batch> historyBatches(String userId,
                                                                              List<AgentRun> runs,
                                                                              List<AgentArtifact> artifacts,
                                                                              int safeLimit) {
        Map<String, List<AgentArtifact>> artifactsByRun = new LinkedHashMap<>();
        for (AgentArtifact artifact : safeArtifacts(artifacts)) {
            if (!isImageArtifact(artifact)) {
                continue;
            }
            String runId = firstText(artifact.getRunId(), artifact.getSessionId(), artifact.getArtifactId());
            artifactsByRun.computeIfAbsent(runId, key -> new ArrayList<>()).add(artifact);
        }

        List<AgentWorkspaceImageHistoryResponse.Batch> batches = new ArrayList<>();
        for (AgentRun run : safeRuns(runs)) {
            if (run == null || (StringUtils.hasText(run.getTaskType()) && !TASK_TYPE.equals(run.getTaskType()))) {
                continue;
            }
            List<AgentArtifact> runArtifacts = artifactsByRun.remove(firstText(run.getRunId()));
            if (runArtifacts == null || runArtifacts.isEmpty()) {
                continue;
            }
            batches.add(batch(run, runArtifacts, runArguments(userId, run)));
            if (batches.size() >= safeLimit) {
                return batches;
            }
        }

        for (List<AgentArtifact> orphanArtifacts : artifactsByRun.values()) {
            if (orphanArtifacts.isEmpty()) {
                continue;
            }
            batches.add(batch(orphanArtifacts));
            if (batches.size() >= safeLimit) {
                break;
            }
        }
        return batches;
    }

    private AgentWorkspaceImageHistoryResponse.Batch batch(AgentRun run,
                                                              List<AgentArtifact> artifacts,
                                                              Map<String, Object> arguments) {
        AgentWorkspaceImageHistoryResponse.Batch batch = batch(artifacts);
        batch.setRequestId(firstText(run.getRequestId()));
        batch.setSessionId(firstText(run.getSessionId(), batch.getSessionId()));
        batch.setRunId(firstText(run.getRunId(), batch.getRunId()));
        batch.setPrompt(firstText(run.getQuestion(), batch.getPrompt()));
        batch.setSummary(firstText(run.getFinalSummary(), batch.getSummary()));
        batch.setStatus(firstText(run.getStatus(), batch.getStatus()));
        batch.setMode(firstText(arguments.get("mode"), batch.getMode()));
        batch.setModel(firstText(arguments.get("model"), batch.getModel()));
        batch.setQuality(firstText(arguments.get("quality"), batch.getQuality()));
        batch.setAspectRatio(firstText(arguments.get("aspectRatio"), batch.getAspectRatio()));
        batch.setSize(firstText(arguments.get("size"), batch.getSize()));
        batch.setBatchCount(intValue(arguments.get("batchCount"), batch.getBatchCount()));
        batch.setSourceImageCount(listSize(arguments.get("sourceImageUrls")));
        batch.setStartedAt(run.getStartedAt());
        batch.setFinishedAt(run.getFinishedAt());
        batch.setDurationMillis(run.getDurationMillis());
        return batch;
    }

    private Map<String, Object> runArguments(String userId, AgentRun run) {
        if (ledgerService == null || run == null || !StringUtils.hasText(run.getRunId())) {
            return Map.of();
        }
        try {
            AgentRunDetailResponse detail = ledgerService.queryRunDetail(userId, run.getRunId());
            if (detail == null || detail.getToolInvocations() == null) {
                return Map.of();
            }
            return detail.getToolInvocations().stream()
                    .filter(this::isImageToolInvocation)
                    .findFirst()
                    .map(invocation -> jsonMap(invocation.getArgumentsJson()))
                    .orElse(Map.of());
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private boolean isImageToolInvocation(AgentRunDetailResponse.ToolInvocation invocation) {
        if (invocation == null) {
            return false;
        }
        return AgentToolOutputNames.IMAGE_GENERATION.equals(invocation.getToolName())
                || ACTION.equals(invocation.getAction());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Object value = objectMapper.readValue(json, Map.class);
            return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private AgentWorkspaceImageHistoryResponse.Batch batch(List<AgentArtifact> artifacts) {
        AgentWorkspaceImageHistoryResponse.Batch batch = new AgentWorkspaceImageHistoryResponse.Batch();
        AgentArtifact first = artifacts.getFirst();
        batch.setSessionId(firstText(first.getSessionId()));
        batch.setRunId(firstText(first.getRunId()));
        batch.setPrompt(firstText(first.getTitle(), "图像生成"));
        batch.setSummary(firstText(first.getTitle(), "图像生成结果"));
        batch.setStatus(AgentRun.STATUS_SUCCESS);
        batch.setMode("generate");
        batch.setModel(AgentImageGenerationPort.DEFAULT_MODEL);
        batch.setQuality(AgentImageGenerationPort.DEFAULT_QUALITY);
        batch.setAspectRatio(AgentImageGenerationPort.DEFAULT_ASPECT_RATIO);
        batch.setSize("");
        batch.setImages(artifacts.stream().map(this::artifactRef).toList());
        batch.setBatchCount(batch.getImages().size());
        batch.setSourceImageCount(0);
        batch.setStartedAt(first.getCreateTime());
        batch.setFinishedAt(first.getCreateTime());
        return batch;
    }

    private void saveSession(String userId, String sessionId, String prompt) {
        AgentSession session = new AgentSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTitle(limit(prompt, 80));
        session.setTaskType(TASK_TYPE);
        session.setLastMessage(limit(prompt, 240));
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(session.getCreateTime());
        try {
            agentRepository.saveSessionIfAbsent(session);
        } catch (Exception ignored) {
        }
    }

    private void updateSession(String userId, String sessionId, String prompt, String summary) {
        AgentSession session = new AgentSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTitle(limit(prompt, 80));
        session.setTaskType(TASK_TYPE);
        session.setLastMessage(firstText(limit(summary, 240), limit(prompt, 240)));
        session.setUpdateTime(LocalDateTime.now());
        try {
            agentRepository.updateSession(session);
        } catch (Exception ignored) {
        }
    }

    private Map<String, Object> arguments(String userId,
                                          AgentWorkspaceImageGenerateRequest request,
                                          UserModelConfig runtimeModelConfig) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        List<String> sourceImageUrls = new ArrayList<>();
        sourceImageUrls.addAll(request.getSourceImageUrls() == null ? List.of() : request.getSourceImageUrls());
        sourceImageUrls.addAll(resolveSourceFileUrls(userId, request.getSourceFileIds()));
        arguments.put("prompt", request.getPrompt().trim());
        arguments.put("mode", sourceImageUrls.isEmpty() ? "generate" : "edit");
        arguments.put("model", firstText(request.getModel(),
                runtimeModelConfig == null ? "" : runtimeModelConfig.getImageModel(),
                AgentImageGenerationPort.DEFAULT_MODEL));
        arguments.put("quality", firstText(request.getQuality(), AgentImageGenerationPort.DEFAULT_QUALITY));
        arguments.put("aspectRatio", firstText(request.getAspectRatio(), AgentImageGenerationPort.DEFAULT_ASPECT_RATIO));
        arguments.put("size", firstText(request.getSize(), "1024x1024"));
        arguments.put("batchCount", Math.max(1, Math.min(10, request.getBatchCount() == null ? 1 : request.getBatchCount())));
        arguments.put("sourceImageUrls", sourceImageUrls);
        arguments.put("maskImageUrls", request.getMaskImageUrls() == null ? List.of() : request.getMaskImageUrls());
        return arguments;
    }

    private List<String> resolveSourceFileUrls(String userId, List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        return fileIds.stream()
                .map(fileId -> firstText(fileId))
                .filter(StringUtils::hasText)
                .map(fileId -> agentRepository.queryFile(userId, fileId)
                        .map(AgentFile::getObjectUrl)
                        .orElse(""))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<AgentWorkspaceImageGenerateResponse.ArtifactRef> saveArtifacts(String userId,
                                                                                   String sessionId,
                                                                                   String runId,
                                                                                   String invocationId,
                                                                                   List<AgentToolFileRef> fileRefs) {
        List<AgentWorkspaceImageGenerateResponse.ArtifactRef> artifacts = new ArrayList<>();
        for (AgentToolFileRef fileRef : fileRefs == null ? List.<AgentToolFileRef>of() : fileRefs) {
            AgentArtifact artifact = artifact(userId, sessionId, runId, invocationId, fileRef);
            try {
                agentRepository.saveArtifact(artifact);
            } catch (Exception ignored) {
            }
            artifacts.add(artifactRef(artifact));
        }
        return artifacts;
    }

    private AgentArtifact artifact(String userId,
                                      String sessionId,
                                      String runId,
                                      String invocationId,
                                      AgentToolFileRef fileRef) {
        AgentArtifact artifact = new AgentArtifact();
        artifact.setArtifactId(firstText(fileRef.getArtifactId(), "IMGART" + UUID.randomUUID().toString().replace("-", "")));
        artifact.setSessionId(sessionId);
        artifact.setUserId(userId);
        artifact.setRunId(runId);
        artifact.setToolInvocationId(invocationId);
        artifact.setSourceType("TOOL");
        artifact.setSourceName(AgentToolOutputNames.IMAGE_GENERATION);
        artifact.setArtifactType("IMAGE");
        artifact.setTitle(firstText(fileRef.getFileName(), "生成图片"));
        artifact.setContent(firstText(fileRef.getPreviewUrl(), fileRef.getDownloadUrl(), fileRef.getFileName()));
        artifact.setDownloadUrl(firstText(fileRef.getDownloadUrl(), fileRef.getPreviewUrl()));
        artifact.setCreateTime(LocalDateTime.now());
        return artifact;
    }

    private AgentWorkspaceImageGenerateResponse response(String requestId,
                                                            String sessionId,
                                                            String runId,
                                                            String invocationId,
                                                            AgentToolStructuredOutput output,
                                                            List<AgentWorkspaceImageGenerateResponse.ArtifactRef> artifacts) {
        AgentWorkspaceImageGenerateResponse response = new AgentWorkspaceImageGenerateResponse();
        response.setRequestId(requestId);
        response.setSessionId(sessionId);
        response.setRunId(runId);
        response.setInvocationId(invocationId);
        response.setToolName(output.getToolName());
        response.setTitle(output.getTitle());
        response.setSummary(output.getSummary());
        response.setMetadata(output.getMetadata());
        response.setProvider(firstText(output.getMetadata().get("provider")));
        Object fallback = output.getMetadata().get("usedFallback");
        response.setUsedFallback(fallback instanceof Boolean bool ? bool : Boolean.parseBoolean(firstText(fallback)));
        response.setFileRefs(fileRefs(output.getFileRefs(), artifacts));
        response.setArtifactRefs(artifacts);
        return response;
    }

    private List<AgentWorkspaceImageGenerateResponse.FileRef> fileRefs(
            List<AgentToolFileRef> fileRefs,
            List<AgentWorkspaceImageGenerateResponse.ArtifactRef> artifacts) {
        List<AgentWorkspaceImageGenerateResponse.FileRef> result = new ArrayList<>();
        for (int index = 0; index < (fileRefs == null ? 0 : fileRefs.size()); index++) {
            AgentToolFileRef source = fileRefs.get(index);
            AgentWorkspaceImageGenerateResponse.FileRef target = new AgentWorkspaceImageGenerateResponse.FileRef();
            target.setArtifactId(firstText(source.getArtifactId(),
                    index < artifacts.size() ? artifacts.get(index).getArtifactId() : ""));
            target.setFileName(source.getFileName());
            target.setDownloadUrl(source.getDownloadUrl());
            target.setPreviewUrl(source.getPreviewUrl());
            target.setContentType(source.getContentType());
            target.setFileSize(source.getFileSize());
            result.add(target);
        }
        return result;
    }

    private AgentWorkspaceImageGenerateResponse.ArtifactRef artifactRef(AgentArtifact artifact) {
        AgentWorkspaceImageGenerateResponse.ArtifactRef ref = new AgentWorkspaceImageGenerateResponse.ArtifactRef();
        ref.setArtifactId(artifact.getArtifactId());
        ref.setSessionId(artifact.getSessionId());
        ref.setRunId(artifact.getRunId());
        ref.setToolInvocationId(artifact.getToolInvocationId());
        ref.setArtifactType(artifact.getArtifactType());
        ref.setTitle(artifact.getTitle());
        ref.setFileName(fileName(artifact));
        ref.setDownloadUrl(artifact.getDownloadUrl());
        ref.setPreviewUrl(firstText(artifact.getContent(), artifact.getDownloadUrl()));
        return ref;
    }

    private boolean isImageArtifact(AgentArtifact artifact) {
        if (artifact == null) {
            return false;
        }
        String type = firstText(artifact.getArtifactType(), artifact.getTitle(), artifact.getContent(), artifact.getDownloadUrl())
                .toLowerCase();
        return type.contains("image")
                || type.endsWith(".png")
                || type.endsWith(".jpg")
                || type.endsWith(".jpeg")
                || type.endsWith(".webp")
                || type.endsWith(".gif");
    }

    private void recordFailure(AgentRun run,
                               String invocationId,
                               String code,
                               String message,
                               long startedAt) {
        ledgerService.recordToolFinish(invocationId, AgentRun.STATUS_FAILED,
                "", "", 0, firstText(message, "后端绘图模型异常，请检查图像模型配置后重试"), elapsed(startedAt));
        ledgerService.finishRun(run, AgentRun.STATUS_FAILED,
                "", firstText(code, "IMAGE_WORKSPACE_0003"), firstText(message, "后端绘图模型异常，请检查图像模型配置后重试"), elapsed(startedAt));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private long elapsed(long startedAt) {
        return Math.max(0L, System.currentTimeMillis() - startedAt);
    }

    private String fileName(AgentArtifact artifact) {
        String value = firstText(artifact.getTitle(), artifact.getContent(), artifact.getDownloadUrl());
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash >= 0 && slash + 1 < value.length() ? value.substring(slash + 1) : value;
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            return Math.max(0, Integer.parseInt(firstText(value)));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private int listSize(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return 1;
        }
        return 0;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = value == null ? "" : String.valueOf(value).trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private String limit(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
















