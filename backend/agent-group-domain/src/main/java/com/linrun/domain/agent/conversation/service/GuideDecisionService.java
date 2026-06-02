package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.adapter.GuideDataRepository;
import com.linrun.domain.agent.conversation.model.GuideDecisionResult;
import com.linrun.domain.agent.conversation.model.GuideIntent;
import com.linrun.domain.agent.conversation.model.GuideIntentType;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideReference;
import com.linrun.domain.agent.conversation.model.RecommendationResult;
import com.linrun.domain.agent.conversation.model.UserRequirement;
import com.linrun.domain.activity.model.GroupBuyActivityStatus;
import com.linrun.domain.activity.model.GroupBuyTrialResult;
import com.linrun.domain.activity.service.GroupBuyActivityService;
import com.linrun.types.exception.AppException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class GuideDecisionService {

    private final GuideDataRepository guideDataRepository;
    private final GroupBuyActivityService groupBuyActivityService;
    private final GuideIntentRecognitionService guideIntentRecognitionService;

    public GuideDecisionService(GuideDataRepository guideDataRepository, GroupBuyActivityService groupBuyActivityService) {
        this(guideDataRepository, groupBuyActivityService, new GuideIntentRecognitionService());
    }

    @Autowired
    public GuideDecisionService(GuideDataRepository guideDataRepository,
                                GroupBuyActivityService groupBuyActivityService,
                                GuideIntentRecognitionService guideIntentRecognitionService) {
        this.guideDataRepository = guideDataRepository;
        this.groupBuyActivityService = groupBuyActivityService;
        this.guideIntentRecognitionService = guideIntentRecognitionService == null
                ? new GuideIntentRecognitionService()
                : guideIntentRecognitionService;
    }

    public GuideDecisionResult decide(String question) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "问题不能为空");
        }

        GuideIntent intent = recognizeIntent(question);
        UserRequirement requirement = UserRequirement.fromIntent(intent);
        List<GuideReference> references = guideDataRepository.queryReferences(question, 5);
        List<GuideProduct> candidates = queryCandidateProducts(question);
        candidates.forEach(candidate -> enrichProductWithGroupBuy(candidate, groupBuyActivityService.trial(candidate.getGoodsId())));
        GuideProduct product = candidates.stream()
                .max(Comparator.comparingInt(candidate -> scoreProduct(requirement, candidate, question)))
                .orElseThrow(() -> new AppException("DATA_0002", "没有可推荐额度包，请先初始化额度包数据"));
        GroupBuyTrialResult groupBuyTrialResult = groupBuyActivityService.trial(product.getGoodsId());
        enrichProductWithGroupBuy(product, groupBuyTrialResult);
        RecommendationResult recommendationResult = buildRecommendation(question, requirement, product, groupBuyTrialResult, candidates);

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
        return guideIntentRecognitionService.recognize(question);
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

    private RecommendationResult buildRecommendation(String question,
                                                     UserRequirement requirement,
                                                     GuideProduct product,
                                                     GroupBuyTrialResult groupBuyTrialResult,
                                                     List<GuideProduct> candidates) {
        RecommendationResult result = new RecommendationResult();
        result.setPrimaryProduct(product);
        candidates.stream()
                .sorted((left, right) -> Integer.compare(scoreProduct(requirement, right, question), scoreProduct(requirement, left, question)))
                .forEach(result::addCandidate);

        result.addReason("SCENARIO_MATCH", "这款额度包和" + String.join("、", requirement.getUsageScenarios()) + "场景匹配。", 90);
        result.addReason("PERSONALIZED_RANK", "本轮已按你的任务、预算和额度需求对候选额度包重新排序。", 92);
        if (requirement.isBudgetSensitive()) {
            result.addReason("BUDGET_MATCH", "你提到了预算或价格因素，所以优先比较拼团价、直接购买价和长期使用成本。", 95);
        }
        if (requirement.getBudgetUpperLimit() != null) {
            result.addReason("BUDGET_LIMIT_MATCH", "你给出的预算上限约为" + requirement.getBudgetUpperLimit().toPlainString()
                    + "元，推荐时会优先排除超预算过多的方案。", 96);
        }
        if (requirement.isPerformanceSensitive()) {
            result.addReason("HIGH_USAGE_MATCH", "你提到了高消耗学术任务，系统会优先检查额度数量和适用任务边界。", 90);
        }
        if (requirement.isPortabilitySensitive()) {
            result.addReason("LIGHT_USAGE_MATCH", "你提到了轻量任务，系统会优先控制购买成本和额度数量。", 82);
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
                ? "推荐额度包、价格、额度数量和推荐理由完整"
                : "推荐额度包信息不完整，需要运营侧补全额度包资料");
        return result;
    }

    private List<String> buildAnswerSegments(RecommendationResult recommendationResult) {
        List<String> segments = new ArrayList<>();
        GuideProduct product = recommendationResult.getPrimaryProduct();
        segments.add("我先从已入库的额度包、活动和知识片段里筛选，并结合你的预算、任务和额度需求重新排序，本轮优先推荐" + product.getGoodsName() + "。");
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

    private int scoreProduct(UserRequirement requirement, GuideProduct product, String question) {
        String text = productText(product);
        String normalizedQuestion = question == null ? "" : question.toLowerCase();
        int score = 50;
        score += preferredGoodsScore(requirement, product, normalizedQuestion);
        if ("学术用户".equals(requirement.getUserIdentity()) && containsAny(text, "学术", "论文", "文献", "PPT", "图表", "研究")) {
            score += 24;
        }
        if (requirement.isBudgetSensitive() && containsAny(text, "性价比", "预算", "省钱", "低价", "拼团价", "基础额度")) {
            score += 18;
        }
        if (requirement.isPerformanceSensitive()) {
            score += containsAny(text, "论文阅读", "ppt", "图表", "深度研究", "长报告", "批量", "复现", "团队") ? 34 : -12;
            score += containsAny(nullToBlank(product.getNotSuitableFor()), "长文档", "批量", "复杂") ? -42 : 0;
        }
        if (requirement.isPortabilitySensitive()) {
            score += containsAny(text, "基础", "轻量", "普通", "摘要", "资料整理") ? 20 : 0;
        }
        for (String scenario : requirement.getUsageScenarios()) {
            score += scenarioMatchScore(scenario, text);
        }
        if (requirement.getBudgetUpperLimit() != null) {
            score += budgetScore(requirement.getBudgetUpperLimit(), product);
        }
        return score;
    }

    private int preferredGoodsScore(UserRequirement requirement, GuideProduct product, String normalizedQuestion) {
        String goodsId = nullToBlank(product.getGoodsId());
        List<String> scenarios = requirement.getUsageScenarios();
        int score = 0;
        if (containsAny(normalizedQuestion, "论文", "文献", "pdf", "精读", "相关工作") && "G10002".equals(goodsId)) {
            score += 90;
        }
        if (containsAny(normalizedQuestion, "普通问答", "摘要", "轻量", "预算有限", "便宜") && "G10001".equals(goodsId)) {
            score += 40;
        }
        if (containsAny(normalizedQuestion, "ppt", "汇报", "答辩", "组会", "演示稿") && "G10003".equals(goodsId)) {
            score += 90;
        }
        if (containsAny(normalizedQuestion, "图表", "流程图", "架构图", "mermaid", "重建") && "G10004".equals(goodsId)) {
            score += 90;
        }
        if (containsAny(normalizedQuestion, "深度研究", "调研", "技术路线", "长报告", "复现") && "G10005".equals(goodsId)) {
            score += 90;
        }
        if (containsAny(normalizedQuestion, "团队", "实验室", "小组", "多人", "共享") && "G10006".equals(goodsId)) {
            score += 90;
        }
        if (scenarios.contains("论文阅读") && "G10002".equals(goodsId)) {
            score += 70;
        }
        if (scenarios.contains("PPT 创作") && "G10003".equals(goodsId)) {
            score += 64;
        }
        if (scenarios.contains("图表重建") && "G10004".equals(goodsId)) {
            score += 70;
        }
        if (scenarios.contains("深度研究") && "G10005".equals(goodsId)) {
            score += 46;
        }
        if (scenarios.contains("团队共享") && "G10006".equals(goodsId)) {
            score += 58;
        }
        if (scenarios.contains("普通学术问答") && requirement.isBudgetSensitive() && "G10001".equals(goodsId)) {
            score += 44;
        }
        return score;
    }

    private int scenarioMatchScore(String scenario, String text) {
        if ("论文阅读".equals(scenario)) {
            return containsAny(text, "论文", "文献", "精读", "相关工作", "复现") ? 22 : 0;
        }
        if ("PPT 创作".equals(scenario)) {
            return containsAny(text, "ppt", "汇报", "答辩", "演示稿", "讲稿") ? 22 : 0;
        }
        if ("图表重建".equals(scenario)) {
            return containsAny(text, "图表", "流程图", "架构图", "mermaid", "重建") ? 24 : 0;
        }
        if ("深度研究".equals(scenario)) {
            return containsAny(text, "深度研究", "调研", "技术路线", "长报告", "复杂主题") ? 30 : -8;
        }
        if ("团队共享".equals(scenario)) {
            return containsAny(text, "团队", "实验室", "共享", "多人", "小组") ? 26 : 0;
        }
        return containsAny(text, "基础", "普通", "摘要", "资料整理", "轻量") ? 8 : 0;
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
