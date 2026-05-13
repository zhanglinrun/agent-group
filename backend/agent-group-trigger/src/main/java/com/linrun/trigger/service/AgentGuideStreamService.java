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
import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AgentGuideStreamService {

    private final GuideDataRepository guideDataRepository;

    public AgentGuideStreamService(GuideDataRepository guideDataRepository) {
        this.guideDataRepository = guideDataRepository;
    }

    public List<GuideStreamEvent<?>> buildEvents(GuideStreamRequest request, String sessionId, String requestId) {
        List<GuideStreamEvent<?>> events = new ArrayList<>();
        AtomicInteger sequence = new AtomicInteger(1);

        if (!StringUtils.hasText(request.getQuestion())) {
            add(events, sessionId, requestId, sequence, GuideEventType.ERROR, error("0001", "问题不能为空"));
            return events;
        }

        add(events, sessionId, requestId, sequence, GuideEventType.TOOL_CALL,
                toolCall("intent_recognize", "正在识别用户预算、使用场景和购买限制"));

        List<GuideReference> references;
        Optional<GuideProduct> recommendProduct;
        try {
            references = guideDataRepository.queryReferences(request.getQuestion(), 3);
            recommendProduct = guideDataRepository.queryRecommendProduct(request.getQuestion());
        } catch (Exception e) {
            add(events, sessionId, requestId, sequence, GuideEventType.ERROR,
                    error("DATA_0001", "导购数据源不可用，请先启动本地 Docker 基础设施并初始化数据"));
            return events;
        }

        if (recommendProduct.isEmpty()) {
            add(events, sessionId, requestId, sequence, GuideEventType.ERROR,
                    error("DATA_0002", "没有可推荐商品，请先初始化商品数据"));
            return events;
        }

        for (GuideReference reference : references) {
            add(events, sessionId, requestId, sequence, GuideEventType.REFERENCE_DELTA, reference(reference));
        }

        GuideProduct product = recommendProduct.get();
        add(events, sessionId, requestId, sequence, GuideEventType.ANSWER_DELTA,
                new AnswerDeltaDTO("我先从已入库的商品、活动和知识片段里筛选，本轮优先推荐" + product.getGoodsName() + "。"));
        add(events, sessionId, requestId, sequence, GuideEventType.ANSWER_DELTA,
                new AnswerDeltaDTO(product.getRecommendReason()));
        add(events, sessionId, requestId, sequence, GuideEventType.PRODUCT_CARD, productCard(product));
        add(events, sessionId, requestId, sequence, GuideEventType.SELF_CHECK, selfCheck());
        add(events, sessionId, requestId, sequence, GuideEventType.DONE, "done");
        return events;
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

    private SelfCheckDTO selfCheck() {
        SelfCheckDTO dto = new SelfCheckDTO();
        dto.setPassed(true);
        dto.setMessage("回答依据、商品价格和推荐理由已完成自检");
        return dto;
    }

    private ErrorDTO error(String code, String message) {
        ErrorDTO dto = new ErrorDTO();
        dto.setCode(code);
        dto.setMessage(message);
        return dto;
    }
}
