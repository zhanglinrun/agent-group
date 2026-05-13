package com.linrun.domain.guide.service;

import com.linrun.domain.guide.adapter.GuideDataRepository;
import com.linrun.domain.guide.model.GuideDecisionResult;
import com.linrun.domain.guide.model.GuideIntent;
import com.linrun.domain.guide.model.GuideIntentType;
import com.linrun.domain.guide.model.GuideProduct;
import com.linrun.domain.guide.model.GuideReference;
import com.linrun.domain.guide.model.RecommendationResult;
import com.linrun.domain.guide.model.UserRequirement;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class GuideDecisionService {

    private final GuideDataRepository guideDataRepository;

    public GuideDecisionService(GuideDataRepository guideDataRepository) {
        this.guideDataRepository = guideDataRepository;
    }

    public GuideDecisionResult decide(String question) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "问题不能为空");
        }

        GuideIntent intent = recognizeIntent(question);
        UserRequirement requirement = UserRequirement.fromIntent(intent);
        List<GuideReference> references = guideDataRepository.queryReferences(question, 3);
        GuideProduct product = guideDataRepository.queryRecommendProduct(question)
                .orElseThrow(() -> new AppException("DATA_0002", "没有可推荐商品，请先初始化商品数据"));
        RecommendationResult recommendationResult = buildRecommendation(requirement, product);

        GuideDecisionResult result = new GuideDecisionResult();
        result.setIntent(intent);
        result.setUserRequirement(requirement);
        result.setRecommendationResult(recommendationResult);
        result.setReferences(references);
        result.setProduct(recommendationResult.getPrimaryProduct());
        result.setAnswerSegments(buildAnswerSegments(recommendationResult));
        return result;
    }

    GuideIntent recognizeIntent(String question) {
        String normalized = question == null ? "" : question.toLowerCase();
        GuideIntent intent = new GuideIntent();
        intent.setBudgetSensitive(containsAny(normalized, "预算", "便宜", "性价比", "省钱", "价格"));
        intent.setGroupBuyConcerned(containsAny(normalized, "拼团", "成团", "团购"));
        intent.setAfterSaleConcerned(containsAny(normalized, "售后", "退货", "退款", "质保", "保修"));
        intent.setCompareConcerned(containsAny(normalized, "对比", "比较", "哪款", "区别", "更合适"));
        intent.setUserIdentity(containsAny(normalized, "学生", "大学生", "研究生") ? "学生" : "普通用户");
        intent.setUsageScenarios(recognizeScenarios(normalized));
        intent.setIntentType(resolveIntentType(intent, normalized));
        return intent;
    }

    private List<String> recognizeScenarios(String normalized) {
        List<String> scenarios = new ArrayList<>();
        if (containsAny(normalized, "论文", "文档", "办公")) {
            scenarios.add("文档写作");
        }
        if (containsAny(normalized, "网课", "学习", "课堂")) {
            scenarios.add("网课学习");
        }
        if (containsAny(normalized, "笔记", "手写")) {
            scenarios.add("手写笔记");
        }
        if (containsAny(normalized, "剪视频", "剪辑", "绘图", "大型应用")) {
            scenarios.add("创作应用");
        }
        if (scenarios.isEmpty()) {
            scenarios.add("日常使用");
        }
        return scenarios;
    }

    private GuideIntentType resolveIntentType(GuideIntent intent, String normalized) {
        if (containsAny(normalized, "订单", "支付状态", "物流")) {
            return GuideIntentType.ORDER_QUERY;
        }
        if (intent.isAfterSaleConcerned()) {
            return GuideIntentType.AFTER_SALE;
        }
        if (intent.isGroupBuyConcerned()) {
            return GuideIntentType.GROUP_RULE;
        }
        if (intent.isCompareConcerned()) {
            return GuideIntentType.PRODUCT_COMPARE;
        }
        return GuideIntentType.PRODUCT_RECOMMEND;
    }

    private RecommendationResult buildRecommendation(UserRequirement requirement, GuideProduct product) {
        RecommendationResult result = new RecommendationResult();
        result.setPrimaryProduct(product);
        result.addCandidate(product);

        result.addReason("SCENARIO_MATCH", "这款商品和" + String.join("、", requirement.getUsageScenarios()) + "场景匹配。", 90);
        if (requirement.isBudgetSensitive()) {
            result.addReason("BUDGET_MATCH", "你提到了预算或价格因素，所以优先比较拼团价、直接购买价和长期使用成本。", 95);
        }
        if (requirement.isAfterSaleConcerned()) {
            result.addReason("AFTER_SALE_MATCH", "你关注售后时，需要同时看退货规则、质保周期和未成团退款规则。", 85);
        }
        if (requirement.isGroupBuyConcerned()) {
            result.addReason("GROUP_BUY_MATCH", "你关注拼团时，需要确认成团人数、剩余时间和未成团后的退款处理。", 80);
        }

        boolean passed = product != null
                && StringUtils.hasText(product.getGoodsId())
                && StringUtils.hasText(product.getGoodsName())
                && product.getOriginPrice() != null
                && product.getGroupPrice() != null
                && StringUtils.hasText(product.getSpecSummary())
                && StringUtils.hasText(product.getRecommendReason());
        result.setPassedSelfCheck(passed);
        result.setSelfCheckMessage(passed
                ? "推荐商品、价格、规格和推荐理由完整"
                : "推荐商品信息不完整，需要运营侧补全商品资料");
        return result;
    }

    private List<String> buildAnswerSegments(RecommendationResult recommendationResult) {
        List<String> segments = new ArrayList<>();
        GuideProduct product = recommendationResult.getPrimaryProduct();
        segments.add("我先从已入库的商品、活动和知识片段里筛选，本轮优先推荐" + product.getGoodsName() + "。");
        segments.add(product.getRecommendReason());
        recommendationResult.getReasons().forEach(reason -> segments.add(reason.getContent()));
        return segments;
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
