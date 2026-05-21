package com.linrun.trigger.service;

import com.linrun.api.agent.model.GuideEventType;
import com.linrun.api.agent.request.GuideStreamRequest;
import com.linrun.api.agent.response.AnswerDeltaDTO;
import com.linrun.api.agent.response.ErrorDTO;
import com.linrun.api.agent.response.GuideStreamEvent;
import com.linrun.api.agent.response.GuideUsageMetricsDTO;
import com.linrun.api.agent.response.OrderDeltaDTO;
import com.linrun.api.agent.response.ProductCardDTO;
import com.linrun.api.agent.response.ReferenceDeltaDTO;
import com.linrun.api.agent.response.SelfCheckDTO;
import com.linrun.api.agent.response.ToolCallDTO;
import com.linrun.domain.conversation.model.GuideDecisionResult;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.conversation.model.GuideRagAnswerResult;
import com.linrun.domain.conversation.model.GuideReference;
import com.linrun.domain.conversation.model.GuideTokenUsage;
import com.linrun.domain.conversation.model.GuideUserInput;
import com.linrun.domain.conversation.model.RecommendationResult;
import com.linrun.domain.conversation.service.GuideConversationService;
import com.linrun.domain.conversation.service.GuideDecisionService;
import com.linrun.domain.conversation.service.GuideImageInputService;
import com.linrun.domain.conversation.service.GuideRagAnswerService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AgentGuideStreamService {

    private final GuideDecisionService guideDecisionService;
    private final GuideRagAnswerService guideRagAnswerService;
    private final GuideConversationService guideConversationService;
    private final GuideImageInputService guideImageInputService;
    private final ToolExecutor toolExecutor;
    private final OrderStatusToolService orderStatusToolService;

    public AgentGuideStreamService(GuideDecisionService guideDecisionService,
                                   GuideRagAnswerService guideRagAnswerService,
                                   GuideConversationService guideConversationService,
                                   GuideImageInputService guideImageInputService,
                                   ToolExecutor toolExecutor,
                                   OrderStatusToolService orderStatusToolService) {
        this.guideDecisionService = guideDecisionService;
        this.guideRagAnswerService = guideRagAnswerService;
        this.guideConversationService = guideConversationService;
        this.guideImageInputService = guideImageInputService;
        this.toolExecutor = toolExecutor;
        this.orderStatusToolService = orderStatusToolService;
    }

    public List<GuideStreamEvent<?>> buildEvents(GuideStreamRequest request, String sessionId, String requestId) {
        List<GuideStreamEvent<?>> events = new ArrayList<>();
        streamEvents(request, sessionId, requestId, events::add, () -> false);
        return events;
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
                    toolCall(imageExecution, "已解析图片输入：" + imageSummary))) {
                return;
            }
        }

        if (orderStatusToolService.isOrderQuery(effectiveQuestion)) {
            streamOrderQuery(request, sessionId, requestId, sink, stopped, startNanos, sequence, userInput, effectiveQuestion);
            return;
        }

        ToolExecution<GuideDecisionResult> decisionExecution = toolExecutor.execute(
                "intent_recognize",
                "execute",
                "已识别用户预算、使用场景和购买限制",
                () -> guideDecisionService.decide(effectiveQuestion));
        if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.TOOL_CALL,
                toolCall(decisionExecution, decisionExecution.getMessage()))) {
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

        for (GuideReference reference : decisionResult.getReferences()) {
            if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.REFERENCE_DELTA, reference(reference))) {
                return;
            }
        }

        GuideRagAnswerResult answerResult = guideRagAnswerService.answerWithMetrics(effectiveQuestion, decisionResult);
        List<String> answerSegments = answerResult.getSegments();
        for (String answerSegment : answerSegments) {
            if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.ANSWER_DELTA, new AnswerDeltaDTO(answerSegment))) {
                return;
            }
        }

        if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.PRODUCT_CARD, productCard(decisionResult.getProduct()))) {
            return;
        }
        if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.SELF_CHECK,
                selfCheck(decisionResult.getRecommendationResult()))) {
            return;
        }
        guideConversationService.rememberUserInput(userInput);
        guideConversationService.rememberAssistantAnswer(sessionId, answerSegments);
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
                "order_status",
                "execute",
                "已查询订单状态",
                () -> orderStatusToolService.queryOrderStatusByQuestion(effectiveQuestion, request.getUserId()));
        if (!emit(sink, stopped, sessionId, requestId, sequence, GuideEventType.TOOL_CALL,
                toolCall(orderExecution, orderExecution.getMessage()))) {
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
        ToolCallDTO dto = new ToolCallDTO();
        dto.setToolName(execution.getToolName());
        dto.setAction(execution.getAction());
        dto.setStatus(execution.getStatus());
        dto.setMessage(message);
        dto.setLatencyMillis(execution.getLatencyMillis());
        return dto;
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

    private ProductCardDTO productCard(GuideProduct product) {
        ProductCardDTO dto = new ProductCardDTO();
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

    private SelfCheckDTO selfCheck(RecommendationResult recommendationResult) {
        SelfCheckDTO dto = new SelfCheckDTO();
        dto.setPassed(recommendationResult.isPassedSelfCheck());
        dto.setMessage(recommendationResult.getSelfCheckMessage());
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
}
