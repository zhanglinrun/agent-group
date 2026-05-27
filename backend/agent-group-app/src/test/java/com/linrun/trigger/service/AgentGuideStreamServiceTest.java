package com.linrun.trigger.service;

import com.linrun.api.agent.model.GuideEventType;
import com.linrun.api.agent.request.GuideStreamRequest;
import com.linrun.api.agent.response.ErrorDTO;
import com.linrun.api.agent.response.GuideStreamEvent;
import com.linrun.api.agent.response.GuideUsageMetricsDTO;
import com.linrun.api.agent.response.OrderDeltaDTO;
import com.linrun.api.agent.response.ProductCardDTO;
import com.linrun.api.agent.response.RetrievalProgressDTO;
import com.linrun.api.agent.response.SelfCheckDTO;
import com.linrun.api.agent.response.ToolCallDTO;
import com.linrun.domain.conversation.adapter.GuideDataRepository;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.conversation.model.GuideReference;
import com.linrun.domain.conversation.service.GuideConversationService;
import com.linrun.domain.conversation.service.GuideDecisionService;
import com.linrun.domain.conversation.service.GuideImageInputService;
import com.linrun.domain.conversation.service.GuideRagAnswerService;
import com.linrun.domain.conversation.service.GuideRagPromptBuilder;
import com.linrun.domain.conversation.service.AgentPlannerService;
import com.linrun.domain.conversation.service.AgentToolRegistry;
import com.linrun.domain.conversation.service.KnowledgeSearchToolService;
import com.linrun.domain.marketing.adapter.GroupBuyActivityRepository;
import com.linrun.domain.marketing.model.GroupBuyActivity;
import com.linrun.domain.marketing.service.GroupBuyActivityService;
import com.linrun.infrastructure.conversation.repository.LocalGuideConversationRepository;
import com.linrun.domain.prompt.service.PromptTemplateService;
import com.linrun.infrastructure.prompt.LocalPromptTemplateRepository;
import com.linrun.domain.order.adapter.TradeOrderRepository;
import com.linrun.domain.order.model.entity.PayOrderEntity;
import com.linrun.domain.order.model.entity.RefundOrderEntity;
import com.linrun.domain.order.model.entity.TradeOrderEntity;
import com.linrun.domain.order.model.valobj.PayStatusEnumVO;
import com.linrun.domain.order.model.valobj.TradeBuyTypeEnumVO;
import com.linrun.domain.order.model.valobj.TradeOrderStatusEnumVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGuideStreamServiceTest {

    @Test
    void shouldBuildGuideEventsWithProductAndReferences() {
        AgentGuideStreamService service = streamService(new FakeGuideDataRepository());
        GuideStreamRequest request = new GuideStreamRequest();
        request.setQuestion("我是学生，预算有限，想买适合看网课的平板");

        List<GuideStreamEvent<?>> events = service.buildEvents(request, "S10001", "R10001");

        assertEquals(List.of(
                GuideEventType.TOOL_PLAN.getCode(),
                GuideEventType.RETRIEVAL_PROGRESS.getCode(),
                GuideEventType.TOOL_CALL.getCode(),
                GuideEventType.RETRIEVAL_PROGRESS.getCode(),
                GuideEventType.REFERENCE_DELTA.getCode(),
                GuideEventType.REFERENCE_DELTA.getCode(),
                GuideEventType.TOOL_CALL.getCode(),
                GuideEventType.TOOL_CALL.getCode(),
                GuideEventType.ANSWER_DELTA.getCode(),
                GuideEventType.ANSWER_DELTA.getCode(),
                GuideEventType.ANSWER_DELTA.getCode(),
                GuideEventType.ANSWER_DELTA.getCode(),
                GuideEventType.ANSWER_DELTA.getCode(),
                GuideEventType.PRODUCT_CARD.getCode(),
                GuideEventType.SELF_CHECK.getCode(),
                GuideEventType.USAGE_METRIC.getCode(),
                GuideEventType.DONE.getCode()
        ), events.stream().map(GuideStreamEvent::getEvent).toList());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17),
                events.stream().map(GuideStreamEvent::getSequence).toList());

        RetrievalProgressDTO routeProgress = assertInstanceOf(RetrievalProgressDTO.class, events.get(1).getData());
        assertEquals("knowledge_base", routeProgress.getStrategy());
        assertEquals("route", routeProgress.getStage());
        ProductCardDTO productCard = assertInstanceOf(ProductCardDTO.class, events.get(13).getData());
        assertTrue(productCard.getDecisionId().startsWith("D"));
        assertEquals("G10001", productCard.getGoodsId());
        assertEquals("轻薄学习平板标准版", productCard.getGoodsName());
        assertEquals(new BigDecimal("2099.00"), productCard.getGroupPrice());
        SelfCheckDTO selfCheck = assertInstanceOf(SelfCheckDTO.class, events.get(14).getData());
        assertEquals(Boolean.TRUE, selfCheck.getPassed());
        GuideUsageMetricsDTO usageMetrics = assertInstanceOf(GuideUsageMetricsDTO.class, events.get(15).getData());
        assertTrue(usageMetrics.getTotalLatencyMillis() >= 0);
        ToolCallDTO knowledgeTool = assertInstanceOf(ToolCallDTO.class, events.get(2).getData());
        assertEquals("knowledge-search-v1", knowledgeTool.getToolVersion());
        assertEquals("MEDIUM", knowledgeTool.getRiskLevel());
        assertEquals(Boolean.TRUE, knowledgeTool.getResultCitationRequired());
        assertEquals(List.of("KF10001", "KF10002"), knowledgeTool.getCitationIds());
        assertTrue(knowledgeTool.getToolCallId().startsWith("knowledge_search-"));
        ToolCallDTO groupTrialTool = assertInstanceOf(ToolCallDTO.class, events.get(7).getData());
        assertEquals("group_trial", groupTrialTool.getToolName());
        assertEquals("G10001", groupTrialTool.getArguments().get("goodsId"));
        assertEquals("group-trial-v1", groupTrialTool.getToolVersion());
        assertEquals("HIGH", groupTrialTool.getRiskLevel());
        assertEquals(0, groupTrialTool.getRetryCount());
        assertEquals("推荐商品、价格、规格和推荐理由完整", selfCheck.getMessage());
    }

    @Test
    void shouldReturnErrorWhenQuestionIsBlank() {
        AgentGuideStreamService service = streamService(new FakeGuideDataRepository());
        GuideStreamRequest request = new GuideStreamRequest();
        request.setQuestion(" ");

        List<GuideStreamEvent<?>> events = service.buildEvents(request, "S10001", "R10001");

        assertEquals(1, events.size());
        assertEquals(GuideEventType.ERROR.getCode(), events.get(0).getEvent());
        ErrorDTO error = assertInstanceOf(ErrorDTO.class, events.get(0).getData());
        assertEquals("0001", error.getCode());
        assertEquals("问题不能为空", error.getMessage());
    }

    @Test
    void shouldReturnErrorWhenRepositoryFails() {
        AgentGuideStreamService service = streamService(new FailingGuideDataRepository());
        GuideStreamRequest request = new GuideStreamRequest();
        request.setQuestion("推荐一款学习平板");

        List<GuideStreamEvent<?>> events = service.buildEvents(request, "S10001", "R10001");

        assertEquals(4, events.size());
        assertEquals(GuideEventType.TOOL_PLAN.getCode(), events.get(0).getEvent());
        assertEquals(GuideEventType.RETRIEVAL_PROGRESS.getCode(), events.get(1).getEvent());
        assertEquals(GuideEventType.TOOL_CALL.getCode(), events.get(2).getEvent());
        assertEquals(GuideEventType.ERROR.getCode(), events.get(3).getEvent());
        ErrorDTO error = assertInstanceOf(ErrorDTO.class, events.get(3).getData());
        assertEquals("DATA_0001", error.getCode());
        assertTrue(error.getMessage().contains("导购数据源不可用"));
    }

    @Test
    void shouldUseConversationContextAndImageInputForFollowUp() {
        TrackingGuideDataRepository repository = new TrackingGuideDataRepository();
        AgentGuideStreamService service = streamService(repository);
        GuideStreamRequest firstRequest = new GuideStreamRequest();
        firstRequest.setQuestion("我是学生，预算有限，想买适合看网课的平板");
        service.buildEvents(firstRequest, "S20001", "R20001");

        GuideStreamRequest followUpRequest = new GuideStreamRequest();
        followUpRequest.setQuestion("那拼团失败能退款吗");
        followUpRequest.setImageUrl("local-image://student-pad-price.png");

        List<GuideStreamEvent<?>> events = service.buildEvents(followUpRequest, "S20001", "R20002");

        ToolCallDTO imageToolCall = assertInstanceOf(ToolCallDTO.class, events.get(0).getData());
        assertEquals(GuideEventType.TOOL_CALL.getCode(), events.get(0).getEvent());
        assertEquals("image_parse", imageToolCall.getToolName());
        assertTrue(repository.getLastQuestion().contains("最近对话"));
        assertTrue(repository.getLastQuestion().contains("我是学生，预算有限"));
        assertTrue(repository.getLastQuestion().contains("本轮图片线索"));
        assertTrue(repository.getLastQuestion().contains("图片疑似平板商品或商品截图"));
        assertEquals(GuideEventType.DONE.getCode(), events.get(events.size() - 1).getEvent());
    }

    @Test
    void shouldAcceptImageOnlyQuestion() {
        TrackingGuideDataRepository repository = new TrackingGuideDataRepository();
        AgentGuideStreamService service = streamService(repository);
        GuideStreamRequest request = new GuideStreamRequest();
        request.setImageUrl("local-image://pad-group-price.png");

        List<GuideStreamEvent<?>> events = service.buildEvents(request, "S30001", "R30001");

        assertEquals(GuideEventType.TOOL_CALL.getCode(), events.get(0).getEvent());
        assertEquals(GuideEventType.TOOL_PLAN.getCode(), events.get(1).getEvent());
        assertEquals(GuideEventType.RETRIEVAL_PROGRESS.getCode(), events.get(2).getEvent());
        assertEquals(GuideEventType.TOOL_CALL.getCode(), events.get(3).getEvent());
        assertTrue(repository.getLastQuestion().contains("请根据图片帮我判断商品是否适合购买"));
        assertTrue(repository.getLastQuestion().contains("图片疑似平板商品或商品截图"));
    }

    @Test
    void shouldQueryOrderStatusForOrderIntent() {
        AgentGuideStreamService service = streamService(new FakeGuideDataRepository(), new FakeTradeOrderRepository());
        GuideStreamRequest request = new GuideStreamRequest();
        request.setUserId("U10001");
        request.setQuestion("查一下订单 O10001 的支付状态");

        List<GuideStreamEvent<?>> events = service.buildEvents(request, "S40001", "R40001");

        assertEquals(List.of(
                GuideEventType.TOOL_PLAN.getCode(),
                GuideEventType.TOOL_CALL.getCode(),
                GuideEventType.ORDER_DELTA.getCode(),
                GuideEventType.ANSWER_DELTA.getCode(),
                GuideEventType.USAGE_METRIC.getCode(),
                GuideEventType.DONE.getCode()
        ), events.stream().map(GuideStreamEvent::getEvent).toList());
        OrderDeltaDTO orderDelta = assertInstanceOf(OrderDeltaDTO.class, events.get(2).getData());
        assertEquals("O10001", orderDelta.getOrderNo());
        assertEquals(TradeOrderStatusEnumVO.PAY_SUCCESS.name(), orderDelta.getCurrentStatus());
        assertEquals("已支付", orderDelta.getDisplayStatus());
    }

    private static class FakeGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            return List.of(reference("KF10001", 1), reference("KF10002", 2));
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            GuideProduct product = new GuideProduct();
            product.setGoodsId("G10001");
            product.setGoodsName("轻薄学习平板标准版");
            product.setImageUrl("");
            product.setOriginPrice(new BigDecimal("2399.00"));
            product.setGroupPrice(new BigDecimal("2099.00"));
            product.setSpecSummary("10.9 英寸屏幕，128GB 存储，支持手写笔");
            product.setAfterSalePolicy("7 天无理由退货，1 年质保");
            product.setRecommendReason("预算有限、学习和网课场景下性价比更高");
            product.setNotSuitableFor("长期剪视频或运行大型应用的用户");
            product.setActivityId("A10001");
            product.setTeamSize(3);
            product.setRemainingSeconds(1800);
            return Optional.of(product);
        }

        @Override
        public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
            return queryRecommendProduct(goodsId);
        }

        private GuideReference reference(String fragmentId, int rank) {
            GuideReference reference = new GuideReference();
            reference.setFragmentId(fragmentId);
            reference.setDocumentId("DOC10001");
            reference.setGoodsId("G10001");
            reference.setDocumentType("商品详情");
            reference.setKnowledgeVersion("v1");
            reference.setContent("轻薄学习平板标准版适合写论文、看网课和日常笔记。");
            reference.setRank(rank);
            return reference;
        }
    }

    private static class FailingGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            throw new IllegalStateException("database unavailable");
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            throw new IllegalStateException("database unavailable");
        }

        @Override
        public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
            throw new IllegalStateException("database unavailable");
        }
    }

    private static class TrackingGuideDataRepository extends FakeGuideDataRepository {

        private String lastQuestion;

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            this.lastQuestion = question;
            return super.queryReferences(question, limit);
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            this.lastQuestion = question;
            return super.queryRecommendProduct(question);
        }

        public String getLastQuestion() {
            return lastQuestion;
        }
    }

    private AgentGuideStreamService streamService(GuideDataRepository guideDataRepository) {
        return streamService(guideDataRepository, new EmptyTradeOrderRepository());
    }

    private AgentGuideStreamService streamService(GuideDataRepository guideDataRepository,
                                                  TradeOrderRepository tradeOrderRepository) {
        GroupBuyActivityService groupBuyActivityService = groupBuyService();
        GuideDecisionService guideDecisionService = new GuideDecisionService(guideDataRepository, groupBuyActivityService);
        AgentToolRegistry agentToolRegistry = new AgentToolRegistry();
        return new AgentGuideStreamService(
                guideDecisionService,
                new GuideRagAnswerService(
                        new GuideRagPromptBuilder(new PromptTemplateService(new LocalPromptTemplateRepository())),
                        prompt -> prompt.getFallbackAnswer()),
                new GuideConversationService(new LocalGuideConversationRepository()),
                new GuideImageInputService(),
                new AgentPlannerService(guideDecisionService, agentToolRegistry),
                new KnowledgeSearchToolService(guideDataRepository),
                groupBuyActivityService,
                new ToolExecutor(),
                new OrderStatusToolService(tradeOrderRepository));
    }

    private static class EmptyTradeOrderRepository extends FakeTradeOrderRepository {

        @Override
        public Optional<TradeOrderEntity> queryTradeOrderByOrderId(String orderId) {
            return Optional.empty();
        }
    }

    private static class FakeTradeOrderRepository implements TradeOrderRepository {

        @Override
        public void save(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public void updatePaySuccess(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public void updateGroupSettledByOrderIds(List<String> orderIds) {
        }

        @Override
        public void updateCloseUnpaid(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public void saveRefundOrder(RefundOrderEntity refundOrder) {
        }

        @Override
        public void updateRefunded(TradeOrderEntity tradeOrder, PayOrderEntity payOrder) {
        }

        @Override
        public Optional<RefundOrderEntity> queryRefundOrderByOrderId(String orderId) {
            return Optional.empty();
        }

        @Override
        public Optional<TradeOrderEntity> queryTradeOrderByOrderId(String orderId) {
            TradeOrderEntity order = new TradeOrderEntity();
            order.setOrderId(orderId);
            order.setUserId("U10001");
            order.setGoodsId("G10001");
            order.setGoodsName("轻薄学习平板标准版");
            order.setBuyType(TradeBuyTypeEnumVO.DIRECT);
            order.setOriginAmount(new BigDecimal("2399.00"));
            order.setPayAmount(new BigDecimal("2399.00"));
            order.setOrderStatus(TradeOrderStatusEnumVO.PAY_SUCCESS);
            order.setCreateTime(LocalDateTime.now());
            return Optional.of(order);
        }

        @Override
        public Optional<PayOrderEntity> queryPayOrderByOrderId(String orderId) {
            PayOrderEntity payOrder = PayOrderEntity.waitPay(
                    "P10001",
                    orderId,
                    new BigDecimal("2399.00"),
                    "MOCK_PAY",
                    "mock://pay/P10001",
                    LocalDateTime.now());
            payOrder.setPayStatus(PayStatusEnumVO.SUCCESS);
            return Optional.of(payOrder);
        }
    }

    private GroupBuyActivityService groupBuyService() {
        return new GroupBuyActivityService(new ActiveGroupBuyActivityRepository());
    }

    private static class ActiveGroupBuyActivityRepository implements GroupBuyActivityRepository {

        @Override
        public Optional<GroupBuyActivity> queryByGoodsId(String goodsId) {
            GroupBuyActivity activity = new GroupBuyActivity();
            activity.setId(1L);
            activity.setActivityId("A10001");
            activity.setGoodsId(goodsId);
            activity.setGroupPrice(new BigDecimal("2099.00"));
            activity.setTeamSize(3);
            activity.setStartTime(LocalDateTime.now().minusMinutes(10));
            activity.setEndTime(LocalDateTime.now().plusMinutes(30));
            activity.setEnabled(true);
            return Optional.of(activity);
        }

        @Override
        public Optional<GroupBuyActivity> queryByActivityId(String activityId) {
            return Optional.empty();
        }
    }
}
