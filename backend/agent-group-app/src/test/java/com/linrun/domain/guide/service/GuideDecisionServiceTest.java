package com.linrun.domain.guide.service;

import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.domain.guide.model.GuideDecisionResult;
import com.linrun.domain.guide.model.GuideIntentType;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.domain.guide.model.RecommendationReason;
import com.linrun.domain.groupbuy.adapter.GroupBuyActivityRepository;
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
    void shouldRecognizeBudgetStudentAndRecommendProduct() {
        GuideDecisionService service = new GuideDecisionService(new FakeGuideDataRepository(), groupBuyService());

        GuideDecisionResult result = service.decide("我是学生，预算有限，想买适合写论文和看网课的平板，哪款更合适？");

        assertEquals(GuideIntentType.PRODUCT_COMPARE, result.getIntent().getIntentType());
        assertEquals("学生", result.getIntent().getUserIdentity());
        assertTrue(result.getIntent().isBudgetSensitive());
        assertTrue(result.getIntent().isCompareConcerned());
        assertEquals(List.of("文档写作", "网课学习"), result.getIntent().getUsageScenarios());
        assertEquals("学生", result.getUserRequirement().getUserIdentity());
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
    void shouldRankCreativeProductWhenUserNeedsPerformance() {
        GuideDecisionService service = new GuideDecisionService(new CreativeGuideDataRepository(), groupBuyService());

        GuideDecisionResult result = service.decide("我想剪视频和绘图，预算 3500 以内，标准版和高配版哪个更合适？");

        assertEquals("G10002", result.getProduct().getGoodsId());
        assertTrue(result.getIntent().isPerformanceSensitive());
        assertEquals(new BigDecimal("3500"), result.getIntent().getBudgetUpperLimit());
        assertEquals(2, result.getRecommendationResult().getCandidates().size());
        assertTrue(result.getRecommendationResult().getReasons().stream()
                .map(RecommendationReason::getReasonType)
                .toList()
                .containsAll(List.of("PERSONALIZED_RANK", "PERFORMANCE_MATCH", "BUDGET_LIMIT_MATCH")));
    }

    @Test
    void shouldReturnFailedSelfCheckWhenProductInfoIsIncomplete() {
        GuideDecisionService service = new GuideDecisionService(new IncompleteGuideDataRepository(), groupBuyService());

        GuideDecisionResult result = service.decide("推荐一款学习平板");

        assertEquals("G10003", result.getProduct().getGoodsId());
        assertEquals(1, result.getRecommendationResult().getCandidates().size());
        assertFalse(result.getRecommendationResult().isPassedSelfCheck());
        assertTrue(result.getAnswerSegments().stream().anyMatch(item -> item.contains("资料待补全")));
        assertEquals("推荐商品信息不完整，需要运营侧补全商品资料", result.getRecommendationResult().getSelfCheckMessage());
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

        AppException exception = assertThrows(AppException.class, () -> service.decide("推荐一款平板"));

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
            product.setGoodsName("轻薄学习平板标准版");
            product.setImageUrl("");
            product.setOriginPrice(new BigDecimal("2399.00"));
            product.setSpecSummary("10.9 英寸屏幕，128GB 存储，支持手写笔");
            product.setAfterSalePolicy("7 天无理由退货，1 年质保");
            product.setRecommendReason("预算有限、学习和网课场景下性价比更高");
            product.setNotSuitableFor("长期剪视频或运行大型应用的用户");
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
            creative.setGoodsName("高配创作平板");
            creative.setImageUrl("");
            creative.setOriginPrice(new BigDecimal("3299.00"));
            creative.setSpecSummary("12.1 英寸高刷屏，256GB 存储，适合剪视频、绘图和多任务");
            creative.setAfterSalePolicy("7 天无理由退货，1 年质保");
            creative.setRecommendReason("性能更强，适合创作类应用");
            creative.setNotSuitableFor("只做笔记和看网课且预算有限的用户");
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
            product.setGoodsName("资料待补全平板");
            product.setImageUrl("");
            product.setOriginPrice(new BigDecimal("1999.00"));
            product.setRecommendReason("资料待补全，暂时只能作为候选商品");
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
            reference.setDocumentType("商品详情");
            reference.setKnowledgeVersion("v1");
            reference.setContent("资料待补全平板只有基础价格信息。");
            reference.setRank(rank);
            return reference;
        }
    }
}
