package com.linrun.trigger.http;

import com.linrun.api.agent.model.GuideEventType;
import com.linrun.api.agent.request.GuideStreamRequest;
import com.linrun.api.agent.response.*;
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
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author linrun.com
 * @description
 * @create 2026-05-13 上午9:44
 */
@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/v1/agent")
public class AgentGuideController {

    private final ExecutorService streamExecutor = Executors.newCachedThreadPool();

    @PostMapping(value = "/guide/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter guideStream(@RequestBody GuideStreamRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        streamExecutor.submit(() -> doGuideStream(request, emitter));
        return emitter;
    }

    private void doGuideStream(GuideStreamRequest request, SseEmitter emitter) {
        String sessionId = StringUtils.hasText(request.getSessionId()) ? request.getSessionId() : "S" + System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        AtomicInteger sequence = new AtomicInteger(1);

        try {
            if (!StringUtils.hasText(request.getQuestion())) {
                send(emitter, GuideStreamEvent.of(GuideEventType.ERROR.getCode(), sessionId, requestId, sequence.getAndIncrement(), error("0001", "问题不能为空")));
                emitter.complete();
                return;
            }

            send(emitter, GuideStreamEvent.of(GuideEventType.TOOL_CALL.getCode(), sessionId, requestId, sequence.getAndIncrement(), toolCall("intent_recognize", "识别用户预算、身份和使用场景")));
            send(emitter, GuideStreamEvent.of(GuideEventType.REFERENCE_DELTA.getCode(), sessionId, requestId, sequence.getAndIncrement(), reference("KF10001", "G10001", "商品详情", "轻薄学习平板标准版适合写论文、看网课和日常笔记。", 1)));
            send(emitter, GuideStreamEvent.of(GuideEventType.REFERENCE_DELTA.getCode(), sessionId, requestId, sequence.getAndIncrement(), reference("KF10002", "G10001", "营销规则", "标准版支持 3 人拼团，拼团价比原价低 300 元。", 2)));
            send(emitter, GuideStreamEvent.of(GuideEventType.ANSWER_DELTA.getCode(), sessionId, requestId, sequence.getAndIncrement(), new AnswerDeltaDTO("如果你是学生，并且预算有限，优先建议看标准版。")));
            send(emitter, GuideStreamEvent.of(GuideEventType.ANSWER_DELTA.getCode(), sessionId, requestId, sequence.getAndIncrement(), new AnswerDeltaDTO("它能覆盖写论文、看网课、做笔记这些核心学习场景。")));
            send(emitter, GuideStreamEvent.of(GuideEventType.ANSWER_DELTA.getCode(), sessionId, requestId, sequence.getAndIncrement(), new AnswerDeltaDTO("高配版更适合剪视频或运行大型应用，当前需求下性价比不如标准版。")));
            send(emitter, GuideStreamEvent.of(GuideEventType.PRODUCT_CARD.getCode(), sessionId, requestId, sequence.getAndIncrement(), productCard()));
            send(emitter, GuideStreamEvent.of(GuideEventType.DONE.getCode(), sessionId, requestId, sequence.getAndIncrement(), "done"));
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

    private ReferenceDeltaDTO reference(String fragmentId, String goodsId, String type, String content, int rank) {
        ReferenceDeltaDTO dto = new ReferenceDeltaDTO();
        dto.setFragmentId(fragmentId);
        dto.setDocumentId("DOC10001");
        dto.setGoodsId(goodsId);
        dto.setDocumentType(type);
        dto.setKnowledgeVersion("v1");
        dto.setContent(content);
        dto.setRank(rank);
        return dto;
    }

    private ProductCardDTO productCard() {
        ProductCardDTO dto = new ProductCardDTO();
        dto.setGoodsId("G10001");
        dto.setGoodsName("轻薄学习平板标准版");
        dto.setImageUrl("");
        dto.setOriginPrice(new BigDecimal("2399.00"));
        dto.setGroupPrice(new BigDecimal("2099.00"));
        dto.setSpecSummary("10.9 英寸屏幕，128GB 存储，支持手写笔");
        dto.setAfterSalePolicy("7 天无理由退货，1 年质保");
        dto.setRecommendReason("预算有限、学习和网课场景下性价比更高");
        dto.setNotSuitableFor("长期剪视频或运行大型应用的用户");
        dto.setActivityId("A10001");
        dto.setTeamSize(3);
        dto.setRemainingSeconds(1800);
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
