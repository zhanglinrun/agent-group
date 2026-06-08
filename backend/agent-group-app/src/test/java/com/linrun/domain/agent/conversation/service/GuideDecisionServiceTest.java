package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.adapter.GuideDataRepository;
import com.linrun.domain.agent.conversation.model.GuideDecisionResult;
import com.linrun.domain.agent.conversation.model.GuideIntentType;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.conversation.model.RecommendationReason;
import com.linrun.domain.groupbuy.adapter.repository.GroupBuyActivityRepository;
import com.linrun.domain.groupbuy.model.GroupBuyActivity;
import com.linrun.domain.groupbuy.service.GroupBuyActivityService;
import com.linrun.types.exception.AppException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideDecisionServiceTest {

    @Test
    void shouldRecognizeAcademicUserAndRecommendQuotaPackage() {
        GuideDecisionService service = new GuideDecisionService(new FakeGuideDataRepository(), groupBuyService());

        GuideDecisionResult result = service.decide("我是研究生，预算有限，想用 Agent 做资料整理和日常学术问答，基础额度包哪款更合适？");

        assertEquals(GuideIntentType.PRODUCT_COMPARE, result.getIntent().getIntentType());
        assertEquals("学术用户", result.getIntent().getUserIdentity());
        assertTrue(result.getIntent().isBudgetSensitive());
        assertTrue(result.getIntent().isCompareConcerned());
        assertEquals(List.of("普通学术问答"), result.getIntent().getUsageScenarios());
        assertEquals("学术用户", result.getUserRequirement().getUserIdentity());
        assertTrue(result.getUserRequirement().isBudgetSensitive());
        assertEquals("G10001", result.getProduct().getGoodsId());
        assertEquals("A10001", result.getProduct().getActivityId());
        assertEquals(new BigDecimal("2099.00"), result.getProduct().getGroupPrice());
        assertEquals(3, result.getProduct().getTeamSize());
        assertTrue(result.getProduct().getRemainingSeconds() > 0);
        assertEquals(2, result.getReferences().size());
        assertEquals(1, result.getRecommendationResult().getCandidates().size());
        assertTrue(result.getRecommendationResult().isPassedSelfCheck());
        assertTrue(result.getRecommendationResult().getReasons().stream()
                .map(RecommendationReason::getReasonType)
                .toList()
                .containsAll(List.of("SCENARIO_MATCH", "BUDGET_MATCH")));
        assertTrue(result.getAnswerSegments().stream().anyMatch(item -> item.contains("预算")));
    }

    @Test
    void shouldRecognizeAfterSaleIntent() {
        GuideDecisionService service = new GuideDecisionService(new FakeGuideDataRepository(), groupBuyService());

        GuideDecisionResult result = service.decide("拼团失败会退款吗，售后怎么处理？");

        assertEquals(GuideIntentType.AFTER_SALE, result.getIntent().getIntentType());
        assertTrue(result.getIntent().isAfterSaleConcerned());
        assertTrue(result.getIntent().isGroupBuyConcerned());
        assertTrue(result.getRecommendationResult().getReasons().stream()
                .map(RecommendationReason::getReasonType)
                .toList()
                .containsAll(List.of("AFTER_SALE_MATCH", "GROUP_BUY_MATCH", "GROUP_TRIAL_ACTIVE")));
        assertTrue(result.getAnswerSegments().stream().anyMatch(item -> item.contains("售后")));
    }

    @Test
    void shouldNotTreatTradeRuleAsConcreteOrderQuery() {
        GuideDecisionService service = new GuideDecisionService(new FakeGuideDataRepository(), groupBuyService());

        assertEquals(GuideIntentType.GROUP_RULE,
                service.recognizeIntent("拼团支付成功以后订单就算已成团了吗？").getIntentType());
        assertEquals(GuideIntentType.ORDER_QUERY,
                service.recognizeIntent("查一下订单 O10001 的支付状态。").getIntentType());
    }

    @Test
    void shouldRankPaperReadingPackageWhenUserNeedsAcademicTask() {
        GuideDecisionService service = new GuideDecisionService(new CreativeGuideDataRepository(), groupBuyService());

        GuideDecisionResult result = service.decide("我是研究生，预算 3500 以内，想做论文精读和整理相关工作，基础包和论文阅读包哪个更合适？");

        assertEquals("G10002", result.getProduct().getGoodsId());
        assertTrue(result.getIntent().isPerformanceSensitive());
        assertEquals(new BigDecimal("3500"), result.getIntent().getBudgetUpperLimit());
        assertEquals(2, result.getRecommendationResult().getCandidates().size());
        assertTrue(result.getRecommendationResult().getReasons().stream()
                .map(RecommendationReason::getReasonType)
                .toList()
                .containsAll(List.of("PERSONALIZED_RANK", "HIGH_USAGE_MATCH", "BUDGET_LIMIT_MATCH")));
    }

    @Test
    void shouldReturnFailedSelfCheckWhenProductInfoIsIncomplete() {
        GuideDecisionService service = new GuideDecisionService(new IncompleteGuideDataRepository(), groupBuyService());

        GuideDecisionResult result = service.decide("推荐一款基础额度包");

        assertEquals("G10003", result.getProduct().getGoodsId());
        assertEquals(1, result.getRecommendationResult().getCandidates().size());
        assertFalse(result.getRecommendationResult().isPassedSelfCheck());
        assertTrue(result.getAnswerSegments().stream().anyMatch(item -> item.contains("资料待补全")));
        assertEquals("推荐额度包信息不完整，需要运营侧补全额度包资料", result.getRecommendationResult().getSelfCheckMessage());
    }

    @Test
    void shouldThrowWhenQuestionIsBlank() {
        GuideDecisionService service = new GuideDecisionService(new FakeGuideDataRepository(), groupBuyService());

        AppException exception = assertThrows(AppException.class, () -> service.decide(" "));

        assertEquals("0001", exception.getCode());
        assertEquals("问题不能为空", exception.getMessage());
    }

    @Test
    void shouldThrowWhenProductIsMissing() {
        GuideDecisionService service = new GuideDecisionService(new EmptyGuideDataRepository(), groupBuyService());

        AppException exception = assertThrows(AppException.class, () -> service.decide("推荐一款额度包"));

        assertEquals("DATA_0002", exception.getCode());
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
            product.setGoodsName("基础学术额度包");
            product.setImageUrl("");
            product.setOriginPrice(new BigDecimal("2399.00"));
            product.setSpecSummary("40 次普通学术问答额度，适合摘要、资料整理和日常问答");
            product.setAfterSalePolicy("直接购买支付成功后发放额度，拼团需成团后发放额度");
            product.setRecommendReason("预算有限、普通学术问答和资料整理场景下性价比更高");
            product.setNotSuitableFor("长文档批量精读、复杂复现或团队共享场景");
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
            reference.setDocumentType("额度包资料");
            reference.setKnowledgeVersion("v1");
            reference.setContent("基础学术额度包适合普通问答、摘要和资料整理。");
            reference.setRank(rank);
            return reference;
        }
    }

    private static class EmptyGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            return List.of();
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            return Optional.empty();
        }

        @Override
        public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
            return Optional.empty();
        }
    }

    private static class CreativeGuideDataRepository extends FakeGuideDataRepository {

        @Override
        public List<GuideProduct> queryCandidateProducts(String question, int limit) {
            GuideProduct standard = queryRecommendProduct(question).orElseThrow();
            GuideProduct creative = new GuideProduct();
            creative.setGoodsId("G10002");
            creative.setGoodsName("论文阅读额度包");
            creative.setImageUrl("");
            creative.setOriginPrice(new BigDecimal("3299.00"));
            creative.setSpecSummary("80 次论文阅读额度，适合论文精读、文献总结、相关工作整理和复现分析");
            creative.setAfterSalePolicy("直接购买支付成功后发放额度，拼团需成团后发放额度");
            creative.setRecommendReason("额度更多，适合论文、文献、精读和相关工作等高消耗学术任务");
            creative.setNotSuitableFor("只做普通问答和摘要且预算有限的用户");
            return List.of(standard, creative);
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

    private static class IncompleteGuideDataRepository implements GuideDataRepository {

        @Override
        public List<GuideReference> queryReferences(String question, int limit) {
            return List.of(reference("KF10003", 1));
        }

        @Override
        public Optional<GuideProduct> queryRecommendProduct(String question) {
            GuideProduct product = new GuideProduct();
            product.setGoodsId("G10003");
            product.setGoodsName("资料待补全额度包");
            product.setImageUrl("");
            product.setOriginPrice(new BigDecimal("1999.00"));
            product.setRecommendReason("资料待补全，暂时只能作为候选额度包");
            return Optional.of(product);
        }

        @Override
        public Optional<GuideProduct> queryProductByGoodsId(String goodsId) {
            return queryRecommendProduct(goodsId);
        }

        private GuideReference reference(String fragmentId, int rank) {
            GuideReference reference = new GuideReference();
            reference.setFragmentId(fragmentId);
            reference.setDocumentId("DOC10003");
            reference.setGoodsId("G10003");
            reference.setDocumentType("额度包资料");
            reference.setKnowledgeVersion("v1");
            reference.setContent("资料待补全额度包只有基础价格信息。");
            reference.setRank(rank);
            return reference;
        }
    }
}
