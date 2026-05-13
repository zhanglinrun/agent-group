package com.linrun.trigger.http;

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
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 导购流式接口。
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/agent")
public class AgentGuideController {

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();
    private final GuideDataRepository guideDataRepository;

    public AgentGuideController(GuideDataRepository guideDataRepository) {
        this.guideDataRepository = guideDataRepository;
    }

    @PostMapping(value = "/guide/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter guideStream(@RequestBody GuideStreamRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        streamExecutor.submit(() -> doGuideStream(request, emitter));
        return emitter;
    }

    private void doGuideStream(GuideStreamRequest request, SseEmitter emitter) {
        String sessionId = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId()
                : "S" + System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        AtomicInteger sequence = new AtomicInteger(1);

        try {
            if (!StringUtils.hasText(request.getQuestion())) {
                send(emitter, GuideStreamEvent.of(
                        GuideEventType.ERROR.getCode(),
                        sessionId,
                        requestId,
                        sequence.getAndIncrement(),
                        error("0001", "问题不能为空")
                ));
                emitter.complete();
                return;
            }

            send(emitter, GuideStreamEvent.of(
                    GuideEventType.TOOL_CALL.getCode(),
                    sessionId,
                    requestId,
                    sequence.getAndIncrement(),
                    toolCall("intent_recognize", "正在识别用户预算、使用场景和购买限制")
            ));

            List<GuideReference> references;
            Optional<GuideProduct> recommendProduct;
            try {
                references = guideDataRepository.queryReferences(request.getQuestion(), 3);
                recommendProduct = guideDataRepository.queryRecommendProduct(request.getQuestion());
            } catch (Exception e) {
                send(emitter, GuideStreamEvent.of(
                        GuideEventType.ERROR.getCode(),
                        sessionId,
                        requestId,
                        sequence.getAndIncrement(),
                        error("DATA_0001", "导购数据源不可用，请先启动本地 Docker 基础设施并初始化数据")
                ));
                emitter.complete();
                return;
            }

            if (recommendProduct.isEmpty()) {
                send(emitter, GuideStreamEvent.of(
                        GuideEventType.ERROR.getCode(),
                        sessionId,
                        requestId,
                        sequence.getAndIncrement(),
                        error("DATA_0002", "没有可推荐商品，请先初始化商品数据")
                ));
                emitter.complete();
                return;
            }

            for (GuideReference reference : references) {
                send(emitter, GuideStreamEvent.of(
                        GuideEventType.REFERENCE_DELTA.getCode(),
                        sessionId,
                        requestId,
                        sequence.getAndIncrement(),
                        reference(reference)
                ));
            }

            GuideProduct product = recommendProduct.get();
            send(emitter, GuideStreamEvent.of(
                    GuideEventType.ANSWER_DELTA.getCode(),
                    sessionId,
                    requestId,
                    sequence.getAndIncrement(),
                    new AnswerDeltaDTO("我先从已入库的商品、活动和知识片段里筛选，本轮优先推荐" + product.getGoodsName() + "。")
            ));

            send(emitter, GuideStreamEvent.of(
                    GuideEventType.ANSWER_DELTA.getCode(),
                    sessionId,
                    requestId,
                    sequence.getAndIncrement(),
                    new AnswerDeltaDTO(product.getRecommendReason())
            ));

            send(emitter, GuideStreamEvent.of(
                    GuideEventType.PRODUCT_CARD.getCode(),
                    sessionId,
                    requestId,
                    sequence.getAndIncrement(),
                    productCard(product)
            ));

            send(emitter, GuideStreamEvent.of(
                    GuideEventType.SELF_CHECK.getCode(),
                    sessionId,
                    requestId,
                    sequence.getAndIncrement(),
                    selfCheck()
            ));

            send(emitter, GuideStreamEvent.of(
                    GuideEventType.DONE.getCode(),
                    sessionId,
                    requestId,
                    sequence.getAndIncrement(),
                    "done"
            ));

            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private <T> void send(SseEmitter emitter, GuideStreamEvent<T> event) throws IOException {
        emitter.send(SseEmitter.event().name(event.getEvent()).data(event, MediaType.APPLICATION_JSON));
        pause();
    }

    private void pause() {
        try {
            Thread.sleep(350L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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

    @PreDestroy
    public void destroy() {
        streamExecutor.shutdown();
    }
}
