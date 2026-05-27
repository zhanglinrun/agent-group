package com.linrun.trigger.http;







import com.linrun.domain.trade.service.OrderStatusToolService;
import com.linrun.domain.support.metrics.AgentObservabilityMetrics;
import com.linrun.trigger.support.tool.ToolExecutor;
import com.linrun.trigger.support.tool.ToolExecution;
import com.linrun.trigger.support.tool.GuideToolEvidenceSelfChecker;
import com.linrun.trigger.support.tool.GuideToolEvidenceCheck;
import com.linrun.api.dto.GuideEventType;
import com.linrun.api.dto.GuideStreamRequest;
import com.linrun.api.dto.AnswerDeltaDTO;
import com.linrun.api.dto.ErrorDTO;
import com.linrun.api.dto.GuideStreamEvent;
import com.linrun.api.dto.GuideUsageMetricsDTO;
import com.linrun.api.dto.OrderDeltaDTO;
import com.linrun.api.dto.ProductCardDTO;
import com.linrun.api.dto.ReferenceDeltaDTO;
import com.linrun.api.dto.RetrievalProgressDTO;
import com.linrun.api.dto.SelfCheckDTO;
import com.linrun.api.dto.ToolCallDTO;
import com.linrun.domain.agent.conversation.model.AgentPlan;
import com.linrun.domain.agent.conversation.model.AgentToolDefinition;
import com.linrun.domain.agent.conversation.model.GuideAnswerReflection;
import com.linrun.domain.agent.conversation.model.GuideDecisionSnapshot;
import com.linrun.domain.agent.conversation.model.GuideDecisionResult;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideQueryRoute;
import com.linrun.domain.agent.conversation.model.GuideRagAnswerResult;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.conversation.model.GuideTokenUsage;
import com.linrun.domain.agent.conversation.model.GuideUserInput;
import com.linrun.domain.agent.conversation.model.KnowledgeSearchResult;
import com.linrun.domain.agent.conversation.model.RecommendationResult;
import com.linrun.domain.agent.conversation.adapter.GuideDecisionSnapshotRepository;
import com.linrun.domain.agent.conversation.adapter.AgentStreamTaskRegistry;
import com.linrun.domain.agent.conversation.service.AgentPlannerService;
import com.linrun.domain.agent.conversation.service.AgentToolRegistry;
import com.linrun.domain.agent.conversation.service.GuideConversationService;
import com.linrun.domain.agent.conversation.service.GuideDecisionService;
import com.linrun.domain.agent.conversation.service.GuideImageInputService;
import com.linrun.domain.agent.conversation.service.GuideRagAnswerService;
import com.linrun.domain.agent.conversation.service.KnowledgeSearchToolService;
import com.linrun.domain.activity.model.GroupBuyTrialResult;
import com.linrun.domain.activity.service.GroupBuyActivityService;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AgentGuideStreamHandler {

    private final GuideDecisionService guideDecisionService;
    private final GuideRagAnswerService guideRagAnswerService;
    private final GuideConversationService guideConversationService;
    private final GuideImageInputService guideImageInputService;
    private final AgentPlannerService agentPlannerService;
    private final AgentToolRegistry agentToolRegistry;
    private final KnowledgeSearchToolService knowledgeSearchToolService;
    private final GroupBuyActivityService groupBuyActivityService;
    private final ToolExecutor toolExecutor;
    private final OrderStatusToolService orderStatusToolService;
    private final GuideDecisionSnapshotRepository guideDecisionSnapshotRepository;
    private final AgentObservabilityMetrics metrics;
    private final AgentStreamTaskRegistry streamTaskRegistry;
    private final GuideToolEvidenceSelfChecker toolEvidenceSelfCheckService;

    public AgentGuideStreamHandler(GuideDecisionService guideDecisionService,
                                   GuideRagAnswerService guideRagAnswerService,
                                   GuideConversationService guideConversationService,
                                   GuideImageInputService guideImageInputService,
                                   AgentPlannerService agentPlannerService,
                                   KnowledgeSearchToolService knowledgeSearchToolService,
                                   GroupBuyActivityService groupBuyActivityService,
                                   ToolExecutor toolExecutor,
                                   OrderStatusToolService orderStatusToolService) {
        this(guideDecisionService, guideRagAnswerService, guideConversationService, guideImageInputService,
                agentPlannerService, new AgentToolRegistry(), knowledgeSearchToolService, groupBuyActivityService, toolExecutor,
                orderStatusToolService, GuideDecisionSnapshotRepository.noop(), AgentObservabilityMetrics.noop(),
                AgentStreamTaskRegistry.noop());
    }

    public AgentGuideStreamHandler(GuideDecisionService guideDecisionService,
                                   GuideRagAnswerService guideRagAnswerService,
                                   GuideConversationService guideConversationService,
                                   GuideImageInputService guideImageInputService,
                                   AgentPlannerService agentPlannerService,
                                   AgentToolRegistry agentToolRegistry,
                                   KnowledgeSearchToolService knowledgeSearchToolService,
                                   GroupBuyActivityService groupBuyActivityService,
                                   ToolExecutor toolExecutor,
                                   OrderStatusToolService orderStatusToolService,
                                   AgentObservabilityMetrics metrics) {
        this(guideDecisionService, guideRagAnswerService, guideConversationService, guideImageInputService,
                agentPlannerService, agentToolRegistry, knowledgeSearchToolService, groupBuyActivityService,
                toolExecutor, orderStatusToolService, GuideDecisionSnapshotRepository.noop(), metrics,
                AgentStreamTaskRegistry.noop());
    }

    @Autowired
    public AgentGuideStreamHandler(GuideDecisionService guideDecisionService,
                                   GuideRagAnswerService guideRagAnswerService,
                                   GuideConversationService guideConversationService,
                                   GuideImageInputService guideImageInputService,
                                   AgentPlannerService agentPlannerService,
                                   AgentToolRegistry agentToolRegistry,
                                   KnowledgeSearchToolService knowledgeSearchToolService,
                                   GroupBuyActivityService groupBuyActivityService,
                                   ToolExecutor toolExecutor,
                                   OrderStatusToolService orderStatusToolService,
                                   GuideDecisionSnapshotRepository guideDecisionSnapshotRepository,
                                   AgentObservabilityMetrics metrics,
                                   AgentStreamTaskRegistry streamTaskRegistry) {
        this.guideDecisionService = guideDecisionService;
        this.guideRagAnswerService = guideRagAnswerService;
        this.guideConversationService = guideConversationService;
        this.guideImageInputService = guideImageInputService;
        this.agentPlannerService = agentPlannerService;
        this.agentToolRegistry = agentToolRegistry == null ? new AgentToolRegistry() : agentToolRegistry;
        this.knowledgeSearchToolService = knowledgeSearchToolService;
        this.groupBuyActivityService = groupBuyActivityService;
        this.toolExecutor = toolExecutor;
        this.orderStatusToolService = orderStatusToolService;
        this.guideDecisionSnapshotRepository = guideDecisionSnapshotRepository == null
                ? GuideDecisionSnapshotRepository.noop()
                : guideDecisionSnapshotRepository;
        this.metrics = metrics == null ? AgentObservabilityMetrics.noop() : metrics;
        this.streamTaskRegistry = streamTaskRegistry == null ? AgentStreamTaskRegistry.noop() : streamTaskRegistry;
        this.toolEvidenceSelfCheckService = new GuideToolEvidenceSelfChecker();
    }

    public List<GuideStreamEvent<?>> buildEvents(GuideStreamRequest request, String sessionId, String requestId) {
        List<GuideStreamEvent<?>> events = new ArrayList<>();
        streamEvents(request, sessionId, requestId, events::add, () -> false);
        return events;
    }

    public Flux<GuideStreamEvent<?>> streamEventFlux(GuideStreamRequest request,
                                                     String sessionId,
                                                     String requestId,
                                                     BooleanSupplier stopped) {
        if (!streamTaskRegistry.register(sessionId, requestId)) {
            return Flux.just(GuideStreamEvent.of(
                    GuideEventType.ERROR.getCode(),
                    sessionId,
                    requestId,
                    1,
                    error("AGENT_0006", "session already has a running stream task")));
        }
        Sinks.Many<GuideStreamEvent<?>> streamSink = Sinks.many().unicast().onBackpressureBuffer();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        Disposable disposable = Schedulers.boundedElastic().schedule(() -> {
            try {
                streamEvents(
                        request,
                        sessionId,
                        requestId,
                        event -> streamSink.tryEmitNext(event),
                        () -> cancelled.get() || (stopped != null && stopped.getAsBoolean()));
                if (!cancelled.get()) {
                    streamSink.tryEmitComplete();
                }
            } catch (Exception e) {
                if (!cancelled.get()) {
                    streamSink.tryEmitError(e);
                }
            }
        });
        streamTaskRegistry.bind(sessionId, requestId, () -> {
            cancelled.set(true);
            disposable.dispose();
            streamSink.tryEmitComplete();
        });
        return streamSink.asFlux()
                .doOnCancel(() -> cancelled.set(true))
                .doFinally(signalType -> streamTaskRegistry.complete(sessionId, requestId));
    }

    public void streamEvents(GuideStreamRequest request,
                             String sessionId,
                             String requestId,
                             Consumer<GuideStreamEvent<?>> sink,
                             BooleanSupplier stopped) {
        long startNanos = System.nanoTime();
        AtomicInteger sequence = new AtomicInteger(1);
        ToolExecution<String> imageExecution = toolExecutor.execute(
                "image_parse",
                "execute",
                "已解析图片输入",
                () -> guideImageInputService.parseImage(request.getImageUrl(), request.getImageName()));
        String imageSummary = imageExecution.getResult();

        if (!StringUtils.hasText(request.getQuestion()) && !StringUtils.hasText(imageSummary)) {
            emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.ERROR, error("0001", "问题不能为空"));
            return;
        }

        GuideUserInput userInput = userInput(request, sessionId, imageSummary);
        String effectiveQuestion = guideConversationService.buildQuestionWithContext(userInput);

        if (StringUtils.hasText(imageSummary)) {
            if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.TOOL_CALL,
                    toolCall(imageExecution, "已解析图片输入：" + imageSummary,
                            Map.of("imageUrl", nullToBlank(request.getImageUrl()))))) {
                return;
            }
        }

        ToolExecution<AgentPlan> planExecution = toolExecutor.execute(
                "agent_plan",
                "plan",
                "已生成工具调用计划",
                () -> agentPlannerService.plan(effectiveQuestion));
        if (!planExecution.isSuccess()) {
            Exception exception = planExecution.getException();
            if (exception instanceof AppException e) {
                emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.ERROR, error(e.getCode(), e.getMessage()));
                return;
            }
            emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.ERROR,
                    error("AGENT_0004", "工具计划生成失败，请稍后重试"));
            return;
        }

        AgentPlan agentPlan = planExecution.getResult();
        if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.TOOL_PLAN, agentPlan)) {
            return;
        }

        if (agentPlan.hasTool(AgentToolRegistry.ORDER_STATUS) || orderStatusToolService.isOrderQuery(effectiveQuestion)) {
            streamOrderQuery(request, sessionId, requestId, sink, stopped, startNanos, sequence, userInput, effectiveQuestion);
            return;
        }

        List<GuideReference> searchedReferences = List.of();
        if (agentPlan.hasTool(AgentToolRegistry.KNOWLEDGE_SEARCH)) {
            GuideQueryRoute route = knowledgeSearchToolService.route(effectiveQuestion);
            if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.RETRIEVAL_PROGRESS,
                    retrievalProgress("route", route, "query routed", null))) {
                return;
            }
            ToolExecution<KnowledgeSearchResult> knowledgeExecution = toolExecutor.execute(
                    agentToolRegistry.requireDefinition(AgentToolRegistry.KNOWLEDGE_SEARCH),
                    "execute",
                    "已检索知识库片段",
                    () -> knowledgeSearchToolService.searchWithRoute(effectiveQuestion, 3));
            if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.TOOL_CALL,
                    toolCall(knowledgeExecution, knowledgeSearchMessage(knowledgeExecution),
                            Map.of("question", effectiveQuestion, "limit", "3")))) {
                return;
            }
            if (!knowledgeExecution.isSuccess()) {
                emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.ERROR,
                        error("DATA_0001", "导购数据源不可用，请先启动本地 Docker 基础设施并初始化数据"));
                return;
            }
            KnowledgeSearchResult searchResult = knowledgeExecution.getResult();
            searchedReferences = searchResult == null ? List.of() : searchResult.getReferences();
            GuideQueryRoute resultRoute = searchResult == null ? route : searchResult.getRoute();
            if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.RETRIEVAL_PROGRESS,
                    retrievalProgress("aggregate", resultRoute, "references ranked", searchedReferences.size()))) {
                return;
            }
            for (GuideReference reference : searchedReferences) {
                if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.REFERENCE_DELTA, reference(reference))) {
                    return;
                }
            }
        }

        ToolExecution<GuideDecisionResult> decisionExecution = toolExecutor.execute(
                agentToolRegistry.requireDefinition(AgentToolRegistry.GUIDE_RECOMMEND),
                "execute",
                "已完成商品推荐、候选排序和决策自检",
                () -> guideDecisionService.decide(effectiveQuestion));
        if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.TOOL_CALL,
                toolCall(decisionExecution, decisionExecution.getMessage(),
                        Map.of("question", effectiveQuestion)))) {
            return;
        }

        GuideDecisionResult decisionResult = decisionExecution.getResult();
        if (!decisionExecution.isSuccess()) {
            Exception exception = decisionExecution.getException();
            if (exception instanceof AppException e) {
                emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.ERROR, error(e.getCode(), e.getMessage()));
                return;
            }
            emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.ERROR,
                    error("DATA_0001", "导购数据源不可用，请先启动本地 Docker 基础设施并初始化数据"));
            return;
        }
        agentPlannerService.fillRuntimeArguments(agentPlan, decisionResult);

        if (searchedReferences.isEmpty()) {
            for (GuideReference reference : decisionResult.getReferences()) {
                if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.REFERENCE_DELTA, reference(reference))) {
                    return;
                }
            }
        }

        ToolExecution<GroupBuyTrialResult> groupTrialExecution = null;
        if (agentPlan.hasTool(AgentToolRegistry.GROUP_TRIAL)
                && decisionResult.getProduct() != null
                && StringUtils.hasText(decisionResult.getProduct().getGoodsId())) {
            groupTrialExecution = toolExecutor.execute(
                    agentToolRegistry.requireDefinition(AgentToolRegistry.GROUP_TRIAL),
                    "execute",
                    "已完成拼团试算",
                    () -> groupBuyActivityService.trial(decisionResult.getProduct().getGoodsId()));
            if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.TOOL_CALL,
                    toolCall(groupTrialExecution, groupTrialMessage(groupTrialExecution),
                            Map.of("goodsId", decisionResult.getProduct().getGoodsId())))) {
                return;
            }
        }

        AtomicBoolean answerStopped = new AtomicBoolean(false);
        GuideRagAnswerResult answerResult = guideRagAnswerService.streamAnswerWithMetrics(
                effectiveQuestion,
                decisionResult,
                answerSegment -> {
                    if (!emit(sink, stopped, sessionId, requestId, sequence,
                            GuideEventType.ANSWER_DELTA, new AnswerDeltaDTO(answerSegment))) {
                        answerStopped.set(true);
                    }
                },
                () -> stopped.getAsBoolean() || answerStopped.get());
        if (answerStopped.get() || stopped.getAsBoolean()) {
            return;
        }

        GuideDecisionSnapshot decisionSnapshot = GuideDecisionSnapshot.capture(
                sessionId,
                requestId,
                request.getUserId(),
                effectiveQuestion,
                decisionResult,
                searchedReferences.isEmpty() ? decisionResult.getReferences() : searchedReferences,
                agentPlan);
        guideDecisionSnapshotRepository.save(decisionSnapshot);

        if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.PRODUCT_CARD,
                productCard(decisionResult.getProduct(), decisionSnapshot))) {
            return;
        }
        List<GuideReference> visibleReferences = searchedReferences.isEmpty()
                ? decisionResult.getReferences()
                : searchedReferences;
        GuideToolEvidenceCheck toolEvidenceCheck = toolEvidenceSelfCheckService.check(
                agentPlan,
                visibleReferences,
                decisionResult,
                groupTrialExecution);
        if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.SELF_CHECK,
                selfCheck(decisionResult.getRecommendationResult(), toolEvidenceCheck, answerResult.getReflection()))) {
            return;
        }
        guideConversationService.rememberUserInput(userInput);
        guideConversationService.rememberAssistantAnswer(sessionId, answerResult.getSegments());
        if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.USAGE_METRIC,
                usageMetrics(answerResult, elapsedMillis(startNanos)))) {
            return;
        }
        emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.DONE, "done");
    }

    private GuideUserInput userInput(GuideStreamRequest request, String sessionId, String imageSummary) {
        GuideUserInput userInput = new GuideUserInput();
        userInput.setSessionId(sessionId);
        userInput.setUserId(request.getUserId());
        userInput.setQuestion(request.getQuestion());
        userInput.setImageUrl(request.getImageUrl());
        userInput.setImageSummary(imageSummary);
        return userInput;
    }

    private void streamOrderQuery(GuideStreamRequest request,
                                  String sessionId,
                                  String requestId,
                                  Consumer<GuideStreamEvent<?>> sink,
                                  BooleanSupplier stopped,
                                  long startNanos,
                                  AtomicInteger sequence,
                                  GuideUserInput userInput,
                                  String effectiveQuestion) {
        ToolExecution<OrderDeltaDTO> orderExecution = toolExecutor.execute(
                agentToolRegistry.requireDefinition(AgentToolRegistry.ORDER_STATUS),
                "execute",
                "已查询订单状态",
                () -> orderStatusToolService.queryOrderStatusByQuestion(effectiveQuestion, request.getUserId()));
        if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.TOOL_CALL,
                toolCall(orderExecution, orderExecution.getMessage(), Map.of("queryMode", "question")))) {
            return;
        }
        if (!orderExecution.isSuccess()) {
            Exception exception = orderExecution.getException();
            if (exception instanceof AppException e) {
                emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.ERROR, error(e.getCode(), e.getMessage()));
                return;
            }
            emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.ERROR,
                    error("TRADE_0019", "订单查询暂不可用，请稍后重试"));
            return;
        }

        OrderDeltaDTO orderDelta = orderExecution.getResult();
        if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.ORDER_DELTA, orderDelta)) {
            return;
        }
        String answer = orderDelta.getMessage();
        if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.ANSWER_DELTA, new AnswerDeltaDTO(answer))) {
            return;
        }
        guideConversationService.rememberUserInput(userInput);
        guideConversationService.rememberAssistantAnswer(sessionId, List.of(answer));
        if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.USAGE_METRIC,
                usageMetricsForToolOnly(elapsedMillis(startNanos)))) {
            return;
        }
        emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.DONE, "done");
    }

    private <T> boolean emit(Consumer<GuideStreamEvent<?>> sink,
                             BooleanSupplier stopped,
                             String sessionId,
                             String requestId,
                             AtomicInteger sequence,
                             GuideEventType eventType,
                             T data) {
        if (stopped.getAsBoolean()) {
            return false;
        }
        sink.accept(GuideStreamEvent.of(eventType.getCode(), sessionId, requestId, sequence.getAndIncrement(), data));
        return !stopped.getAsBoolean();
    }

    private ToolCallDTO toolCall(ToolExecution<?> execution, String message) {
        return toolCall(execution, message, Map.of());
    }

    private ToolCallDTO toolCall(ToolExecution<?> execution, String message, Map<String, String> arguments) {
        ToolCallDTO dto = new ToolCallDTO();
        dto.setToolName(execution.getToolName());
        dto.setAction(execution.getAction());
        dto.setStatus(execution.getStatus());
        dto.setMessage(message);
        dto.setArguments(arguments);
        dto.setLatencyMillis(execution.getLatencyMillis());
        dto.setToolCallId(execution.getToolCallId());
        dto.setRetryCount(execution.getRetryCount());
        dto.setResultDigest(execution.getResultDigest());
        dto.setCitationIds(citationIds(execution));
        agentToolRegistry.findDefinition(execution.getToolName())
                .ifPresent(definition -> fillToolDefinition(dto, definition));
        return dto;
    }

    private void fillToolDefinition(ToolCallDTO dto, AgentToolDefinition definition) {
        dto.setToolVersion(definition.getVersion());
        dto.setRiskLevel(definition.getRiskLevel());
        dto.setResultCitationRequired(definition.isResultCitationRequired());
    }

    private List<String> citationIds(ToolExecution<?> execution) {
        Object result = execution.getResult();
        if (result instanceof GuideDecisionResult decisionResult) {
            return decisionResult.getReferences().stream()
                    .map(GuideReference::getFragmentId)
                    .filter(Objects::nonNull)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        if (result instanceof KnowledgeSearchResult searchResult) {
            return searchResult.getReferences().stream()
                    .map(GuideReference::getFragmentId)
                    .filter(Objects::nonNull)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        if (!(result instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .filter(GuideReference.class::isInstance)
                .map(GuideReference.class::cast)
                .map(GuideReference::getFragmentId)
                .filter(Objects::nonNull)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String knowledgeSearchMessage(ToolExecution<KnowledgeSearchResult> execution) {
        if (!execution.isSuccess()) {
            return "知识库检索失败";
        }
        int count = execution.getResult() == null ? 0 : execution.getResult().getReferences().size();
        return "已检索知识库片段 " + count + " 条";
    }

    private RetrievalProgressDTO retrievalProgress(String stage,
                                                   GuideQueryRoute route,
                                                   String message,
                                                   Integer referenceCount) {
        return RetrievalProgressDTO.of(
                stage,
                route == null ? "" : route.getStrategy(),
                message,
                route == null ? null : route.getConfidence(),
                route == null ? List.of() : route.getRetrievers(),
                referenceCount);
    }

    private String groupTrialMessage(ToolExecution<GroupBuyTrialResult> execution) {
        if (!execution.isSuccess() || execution.getResult() == null) {
            return "拼团试算失败，已保留原推荐结果";
        }
        GroupBuyTrialResult result = execution.getResult();
        if (result.isAvailable()) {
            return "已完成拼团试算：商品 " + result.getGoodsId() + " 拼团价 " + result.getGroupPrice()
                    + "，" + result.getTeamSize() + " 人成团";
        }
        return "已完成拼团试算：" + result.getMessage();
    }

    private ReferenceDeltaDTO reference(GuideReference reference) {
        ReferenceDeltaDTO dto = new ReferenceDeltaDTO();
        dto.setFragmentId(reference.getFragmentId());
        dto.setDocumentId(reference.getDocumentId());
        dto.setGoodsId(reference.getGoodsId());
        dto.setDocumentType(reference.getDocumentType());
        dto.setKnowledgeVersion(reference.getKnowledgeVersion());
        dto.setContent(reference.getContent());
        dto.setRank(reference.getRank());
        return dto;
    }

    private ProductCardDTO productCard(GuideProduct product, GuideDecisionSnapshot decisionSnapshot) {
        ProductCardDTO dto = new ProductCardDTO();
        dto.setDecisionId(decisionSnapshot.getDecisionId());
        dto.setQuoteExpireTime(decisionSnapshot.getQuoteExpireTime());
        dto.setGoodsId(product.getGoodsId());
        dto.setGoodsName(product.getGoodsName());
        dto.setImageUrl(product.getImageUrl());
        dto.setOriginPrice(product.getOriginPrice());
        dto.setGroupPrice(product.getGroupPrice());
        dto.setSpecSummary(product.getSpecSummary());
        dto.setAfterSalePolicy(product.getAfterSalePolicy());
        dto.setRecommendReason(product.getRecommendReason());
        dto.setNotSuitableFor(product.getNotSuitableFor());
        dto.setActivityId(product.getActivityId());
        dto.setTeamSize(product.getTeamSize());
        dto.setRemainingSeconds(product.getRemainingSeconds());
        return dto;
    }

    private SelfCheckDTO selfCheck(RecommendationResult recommendationResult,
                                   GuideToolEvidenceCheck toolEvidenceCheck,
                                   GuideAnswerReflection reflection) {
        GuideToolEvidenceCheck safeToolEvidenceCheck = toolEvidenceCheck == null
                ? GuideToolEvidenceCheck.ok()
                : toolEvidenceCheck;
        GuideAnswerReflection safeReflection = reflection == null
                ? GuideAnswerReflection.passed()
                : reflection;
        SelfCheckDTO dto = new SelfCheckDTO();
        dto.setPassed(recommendationResult.isPassedSelfCheck()
                && safeToolEvidenceCheck.passed()
                && safeReflection.isPassed());
        dto.setMessage(recommendationResult.isPassedSelfCheck()
                ? safeToolEvidenceCheck.passed()
                    ? safeReflection.isPassed() ? recommendationResult.getSelfCheckMessage() : safeReflection.getMessage()
                    : safeToolEvidenceCheck.message()
                : recommendationResult.getSelfCheckMessage());
        return dto;
    }

    private GuideUsageMetricsDTO usageMetrics(GuideRagAnswerResult answerResult, long totalLatencyMillis) {
        GuideUsageMetricsDTO dto = new GuideUsageMetricsDTO();
        GuideTokenUsage tokenUsage = answerResult.getTokenUsage();
        dto.setPromptTokens(tokenUsage.getPromptTokens());
        dto.setCompletionTokens(tokenUsage.getCompletionTokens());
        dto.setTotalTokens(tokenUsage.getTotalTokens());
        dto.setEstimatedCostYuan(tokenUsage.getEstimatedCostYuan());
        dto.setLlmLatencyMillis(answerResult.getLlmLatencyMillis());
        dto.setTotalLatencyMillis(totalLatencyMillis);
        dto.setFallbackUsed(answerResult.isFallbackUsed());
        metrics.recordGuideUsage(answerResult.getLlmLatencyMillis(), totalLatencyMillis,
                tokenUsage.getTotalTokens(), tokenUsage.getEstimatedCostYuan(), answerResult.isFallbackUsed());
        return dto;
    }

    private GuideUsageMetricsDTO usageMetricsForToolOnly(long totalLatencyMillis) {
        GuideUsageMetricsDTO dto = new GuideUsageMetricsDTO();
        dto.setPromptTokens(0L);
        dto.setCompletionTokens(0L);
        dto.setTotalTokens(0L);
        dto.setEstimatedCostYuan(java.math.BigDecimal.ZERO);
        dto.setLlmLatencyMillis(0L);
        dto.setTotalLatencyMillis(totalLatencyMillis);
        dto.setFallbackUsed(false);
        metrics.recordGuideUsage(0L, totalLatencyMillis, 0L, java.math.BigDecimal.ZERO, false);
        return dto;
    }

    private long elapsedMillis(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    private ErrorDTO error(String code, String message) {
        ErrorDTO dto = new ErrorDTO();
        dto.setCode(code);
        dto.setMessage(message);
        return dto;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
