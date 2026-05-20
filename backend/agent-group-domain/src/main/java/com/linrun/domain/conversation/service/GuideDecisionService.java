package com.linrun.domain.conversation.service;

import com.linrun.domain.conversation.adapter.GuideDataRepository;
import com.linrun.domain.conversation.model.GuideDecisionResult;
import com.linrun.domain.conversation.model.GuideIntent;
import com.linrun.domain.conversation.model.GuideIntentType;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.conversation.model.GuideReference;
import com.linrun.domain.conversation.model.RecommendationResult;
import com.linrun.domain.conversation.model.UserRequirement;
import com.linrun.domain.marketing.model.GroupBuyActivityStatus;
import com.linrun.domain.marketing.model.GroupBuyTrialResult;
import com.linrun.domain.marketing.service.GroupBuyActivityService;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GuideDecisionService {

    private final GuideDataRepository guideDataRepository;
    private final GroupBuyActivityService groupBuyActivityService;

    public GuideDecisionService(GuideDataRepository guideDataRepository, GroupBuyActivityService groupBuyActivityService) {
        this.guideDataRepository = guideDataRepository;
        this.groupBuyActivityService = groupBuyActivityService;
    }

    public GuideDecisionResult decide(String question) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "问题不能为空");
        }

        GuideIntent intent = recognizeIntent(question);
        UserRequirement requirement = UserRequirement.fromIntent(intent);
        List<GuideReference> references = guideDataRepository.queryReferences(question, 3);
        List<GuideProduct> candidates = queryCandidateProducts(question);
        candidates.forEach(candidate -> enrichProductWithGroupBuy(candidate, groupBuyActivityService.trial(candidate.getGoodsId())));
        GuideProduct product = candidates.stream()
                .max(Comparator.comparingInt(candidate -> scoreProduct(requirement, candidate)))
                .orElseThrow(() -> new AppException("DATA_0002", "没有可推荐商品，请先初始化商品数据"));
        GroupBuyTrialResult groupBuyTrialResult = groupBuyActivityService.trial(product.getGoodsId());
        enrichProductWithGroupBuy(product, groupBuyTrialResult);
        RecommendationResult recommendationResult = buildRecommendation(requirement, product, groupBuyTrialResult, candidates);

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
        BigDecimal budgetUpperLimit = recognizeBudgetUpperLimit(normalized);
        intent.setBudgetUpperLimit(budgetUpperLimit);
        intent.setBudgetSensitive(budgetUpperLimit != null
                || containsAny(normalized, "预算", "便宜", "性价比", "省钱", "价格", "划算", "低价"));
        intent.setGroupBuyConcerned(containsAny(normalized, "拼团", "成团", "团购"));
        intent.setAfterSaleConcerned(containsAny(normalized, "售后", "退货", "退款", "质保", "保修"));
        intent.setCompareConcerned(containsAny(normalized, "对比", "比较", "哪款", "区别", "更合适"));
        intent.setPerformanceSensitive(containsAny(normalized, "剪视频", "剪辑", "绘图", "大型应用", "高刷", "性能", "多任务", "创作"));
        intent.setPortabilitySensitive(containsAny(normalized, "轻薄", "便携", "携带", "通勤", "宿舍", "课堂"));
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
        if (containsAny(normalized, "轻薄", "便携", "携带", "通勤", "宿舍", "课堂")) {
            scenarios.add("便携学习");
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

    private void enrichProductWithGroupBuy(GuideProduct product, GroupBuyTrialResult trialResult) {
        if (GroupBuyActivityStatus.ACTIVE.equals(trialResult.getStatus())) {
            product.setActivityId(trialResult.getActivityId());
            product.setGroupPrice(trialResult.getGroupPrice());
            product.setTeamSize(trialResult.getTeamSize());
            product.setRemainingSeconds(trialResult.getRemainingSeconds());
            return;
        }
        if (product.getGroupPrice() == null) {
            product.setGroupPrice(product.getOriginPrice());
        }
        if (product.getTeamSize() == null) {
            product.setTeamSize(1);
        }
        if (product.getRemainingSeconds() == null) {
            product.setRemainingSeconds(0);
        }
    }

    private RecommendationResult buildRecommendation(UserRequirement requirement, GuideProduct product,
                                                     GroupBuyTrialResult groupBuyTrialResult,
                                                     List<GuideProduct> candidates) {
        RecommendationResult result = new RecommendationResult();
        result.setPrimaryProduct(product);
        candidates.stream()
                .sorted((left, right) -> Integer.compare(scoreProduct(requirement, right), scoreProduct(requirement, left)))
                .forEach(result::addCandidate);

        result.addReason("SCENARIO_MATCH", "这款商品和" + String.join("、", requirement.getUsageScenarios()) + "场景匹配。", 90);
        result.addReason("PERSONALIZED_RANK", "本轮已按你的身份、预算、用途和购买限制对候选商品重新排序。", 92);
        if (requirement.isBudgetSensitive()) {
            result.addReason("BUDGET_MATCH", "你提到了预算或价格因素，所以优先比较拼团价、直接购买价和长期使用成本。", 95);
        }
        if (requirement.getBudgetUpperLimit() != null) {
            result.addReason("BUDGET_LIMIT_MATCH", "你给出的预算上限约为" + requirement.getBudgetUpperLimit().toPlainString()
                    + "元，推荐时会优先排除超预算过多的方案。", 96);
        }
        if (requirement.isPerformanceSensitive()) {
            result.addReason("PERFORMANCE_MATCH", "你提到了创作或性能场景，系统会优先检查高刷、多任务、剪辑和绘图能力。", 90);
        }
        if (requirement.isPortabilitySensitive()) {
            result.addReason("PORTABILITY_MATCH", "你提到了课堂、宿舍或携带场景，系统会优先关注尺寸、重量和学习使用便利性。", 82);
        }
        if (requirement.isAfterSaleConcerned()) {
            result.addReason("AFTER_SALE_MATCH", "你关注售后时，需要同时看退货规则、质保周期和未成团退款规则。", 85);
        }
        if (requirement.isGroupBuyConcerned()) {
            result.addReason("GROUP_BUY_MATCH", "你关注拼团时，需要确认成团人数、剩余时间和未成团后的退款处理。", 80);
            if (GroupBuyActivityStatus.ACTIVE.equals(groupBuyTrialResult.getStatus())) {
                result.addReason("GROUP_TRIAL_ACTIVE",
                        "当前拼团活动可用，成团人数为" + groupBuyTrialResult.getTeamSize()
                                + "人，剩余" + groupBuyTrialResult.getRemainingSeconds() + "秒。",
                        88);
            } else {
                result.addReason("GROUP_TRIAL_UNAVAILABLE", groupBuyTrialResult.getMessage(), 70);
            }
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
        segments.add("我先从已入库的商品、活动和知识片段里筛选，并结合你的预算、用途和限制重新排序，本轮优先推荐" + product.getGoodsName() + "。");
        segments.add(product.getRecommendReason());
        recommendationResult.getReasons().forEach(reason -> segments.add(reason.getContent()));
        return segments;
    }

    private List<GuideProduct> queryCandidateProducts(String question) {
        List<GuideProduct> candidates = guideDataRepository.queryCandidateProducts(question, 8);
        if (candidates != null && !candidates.isEmpty()) {
            return candidates;
        }
        return guideDataRepository.queryRecommendProduct(question)
                .map(List::of)
                .orElseGet(List::of);
    }

    private int scoreProduct(UserRequirement requirement, GuideProduct product) {
        String text = productText(product);
        int score = 50;
        if ("学生".equals(requirement.getUserIdentity()) && containsAny(text, "学生", "学习", "网课", "论文", "笔记", "轻办公")) {
            score += 24;
        }
        if (requirement.isBudgetSensitive() && containsAny(text, "性价比", "预算", "省钱", "低价", "拼团价")) {
            score += 18;
        }
        if (requirement.isPerformanceSensitive()) {
            score += containsAny(text, "剪视频", "剪辑", "绘图", "大型应用", "高刷", "性能", "多任务", "创作") ? 34 : -12;
            score += containsAny(nullToBlank(product.getNotSuitableFor()), "剪视频", "绘图", "大型应用") ? -42 : 0;
        }
        if (requirement.isPortabilitySensitive()) {
            score += containsAny(text, "轻薄", "便携", "课堂", "网课", "笔记") ? 20 : 0;
        }
        for (String scenario : requirement.getUsageScenarios()) {
            score += scenarioMatchScore(scenario, text);
        }
        if (requirement.getBudgetUpperLimit() != null) {
            score += budgetScore(requirement.getBudgetUpperLimit(), product);
        }
        return score;
    }

    private int scenarioMatchScore(String scenario, String text) {
        if ("文档写作".equals(scenario)) {
            return containsAny(text, "论文", "文档", "办公", "轻办公") ? 18 : 0;
        }
        if ("网课学习".equals(scenario)) {
            return containsAny(text, "网课", "学习", "课堂") ? 18 : 0;
        }
        if ("手写笔记".equals(scenario)) {
            return containsAny(text, "手写", "笔记", "手写笔") ? 16 : 0;
        }
        if ("创作应用".equals(scenario)) {
            return containsAny(text, "剪视频", "剪辑", "绘图", "大型应用", "高刷", "多任务") ? 28 : -10;
        }
        if ("便携学习".equals(scenario)) {
            return containsAny(text, "轻薄", "便携", "10.9", "学习") ? 12 : 0;
        }
        return containsAny(text, "日常", "学习", "办公") ? 6 : 0;
    }

    private int budgetScore(BigDecimal budgetUpperLimit, GuideProduct product) {
        BigDecimal price = product.getGroupPrice() == null ? product.getOriginPrice() : product.getGroupPrice();
        if (price == null) {
            return -20;
        }
        int compared = price.compareTo(budgetUpperLimit);
        if (compared <= 0) {
            return 35;
        }
        BigDecimal over = price.subtract(budgetUpperLimit);
        if (over.compareTo(BigDecimal.valueOf(300)) <= 0) {
            return -6;
        }
        if (over.compareTo(BigDecimal.valueOf(800)) <= 0) {
            return -24;
        }
        return -45;
    }

    private BigDecimal recognizeBudgetUpperLimit(String normalized) {
        List<Pattern> patterns = List.of(
                Pattern.compile("(?:预算|不超过|控制在|低于|少于|最多|以内|以下)[^0-9]{0,8}(\\d{3,5})"),
                Pattern.compile("(\\d{3,5})\\s*(?:元|块|以内|以下|左右)")
        );
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(normalized);
            if (matcher.find()) {
                return new BigDecimal(matcher.group(1));
            }
        }
        return null;
    }

    private String productText(GuideProduct product) {
        return String.join(" ",
                nullToBlank(product.getGoodsName()),
                nullToBlank(product.getSpecSummary()),
                nullToBlank(product.getAfterSalePolicy()),
                nullToBlank(product.getRecommendReason()),
                nullToBlank(product.getNotSuitableFor())).toLowerCase();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
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
