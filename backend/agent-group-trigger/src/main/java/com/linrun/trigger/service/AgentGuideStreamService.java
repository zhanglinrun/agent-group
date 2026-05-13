package com.linrun.trigger.service;

import com.linrun.api.agent.model.GuideEventType;
import com.linrun.api.agent.request.GuideStreamRequest;
import com.linrun.api.agent.response.AnswerDeltaDTO;
import com.linrun.api.agent.response.ErrorDTO;
import com.linrun.api.agent.response.GuideStreamEvent;
import com.linrun.api.agent.response.ProductCardDTO;
import com.linrun.api.agent.response.ReferenceDeltaDTO;
import com.linrun.api.agent.response.SelfCheckDTO;
import com.linrun.api.agent.response.ToolCallDTO;
import com.linrun.domain.guide.model.GuideDecisionResult;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.domain.guide.model.GuideUserInput;
import com.linrun.domain.guide.model.RecommendationResult;
import com.linrun.domain.guide.service.GuideConversationService;
import com.linrun.domain.guide.service.GuideDecisionService;
import com.linrun.domain.guide.service.GuideImageInputService;
import com.linrun.domain.guide.service.GuideRagAnswerService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AgentGuideStreamService {

    private final GuideDecisionService guideDecisionService;
    private final GuideRagAnswerService guideRagAnswerService;
    private final GuideConversationService guideConversationService;
    private final GuideImageInputService guideImageInputService;

    public AgentGuideStreamService(GuideDecisionService guideDecisionService,
                                   GuideRagAnswerService guideRagAnswerService,
                                   GuideConversationService guideConversationService,
                                   GuideImageInputService guideImageInputService) {
        this.guideDecisionService = guideDecisionService;
        this.guideRagAnswerService = guideRagAnswerService;
        this.guideConversationService = guideConversationService;
        this.guideImageInputService = guideImageInputService;
    }

    public List<GuideStreamEvent<?>> buildEvents(GuideStreamRequest request, String sessionId, String requestId) {
        List<GuideStreamEvent<?>> events = new ArrayList<>();
        AtomicInteger sequence = new AtomicInteger(1);
        String imageSummary = guideImageInputService.parseImage(request.getImageUrl());

        if (!StringUtils.hasText(request.getQuestion()) && !StringUtils.hasText(imageSummary)) {
            add(events, sessionId, requestId, sequence, GuideEventType.ERROR, error("0001", "问题不能为空"));
            return events;
        }

        GuideUserInput userInput = userInput(request, sessionId, imageSummary);
        String effectiveQuestion = guideConversationService.buildQuestionWithContext(userInput);

        if (StringUtils.hasText(imageSummary)) {
            add(events, sessionId, requestId, sequence, GuideEventType.TOOL_CALL,
                    toolCall("image_parse", "已解析图片输入：" + imageSummary));
        }

        add(events, sessionId, requestId, sequence, GuideEventType.TOOL_CALL,
                toolCall("intent_recognize", "正在识别用户预算、使用场景和购买限制"));

        GuideDecisionResult decisionResult;
        try {
            decisionResult = guideDecisionService.decide(effectiveQuestion);
        } catch (AppException e) {
            add(events, sessionId, requestId, sequence, GuideEventType.ERROR, error(e.getCode(), e.getMessage()));
            return events;
        } catch (Exception e) {
            add(events, sessionId, requestId, sequence, GuideEventType.ERROR,
                    error("DATA_0001", "导购数据源不可用，请先启动本地 Docker 基础设施并初始化数据"));
            return events;
        }

        for (GuideReference reference : decisionResult.getReferences()) {
            add(events, sessionId, requestId, sequence, GuideEventType.REFERENCE_DELTA, reference(reference));
        }

        List<String> answerSegments = guideRagAnswerService.answer(effectiveQuestion, decisionResult);
        for (String answerSegment : answerSegments) {
            add(events, sessionId, requestId, sequence, GuideEventType.ANSWER_DELTA, new AnswerDeltaDTO(answerSegment));
        }

        add(events, sessionId, requestId, sequence, GuideEventType.PRODUCT_CARD, productCard(decisionResult.getProduct()));
        add(events, sessionId, requestId, sequence, GuideEventType.SELF_CHECK,
                selfCheck(decisionResult.getRecommendationResult()));
        guideConversationService.rememberUserInput(userInput);
        guideConversationService.rememberAssistantAnswer(sessionId, answerSegments);
        add(events, sessionId, requestId, sequence, GuideEventType.DONE, "done");
        return events;
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

    private <T> void add(List<GuideStreamEvent<?>> events, String sessionId, String requestId, AtomicInteger sequence,
                         GuideEventType eventType, T data) {
        events.add(GuideStreamEvent.of(eventType.getCode(), sessionId, requestId, sequence.getAndIncrement(), data));
    }

    private ToolCallDTO toolCall(String toolName, String message) {
        ToolCallDTO dto = new ToolCallDTO();
        dto.setToolName(toolName);
        dto.setAction("mock_execute");
        dto.setStatus("success");
        dto.setMessage(message);
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

    private ErrorDTO error(String code, String message) {
        ErrorDTO dto = new ErrorDTO();
        dto.setCode(code);
        dto.setMessage(message);
        return dto;
    }
}
