package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.model.GuideIntent;
import com.linrun.domain.agent.conversation.model.GuideIntentType;
import com.linrun.types.exception.AppException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GuideIntentRecognitionService {

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("[OP]-?[A-Z0-9-]*\\d{4,}[A-Z0-9-]*", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOODS_ID_PATTERN = Pattern.compile("G\\d{4,}", Pattern.CASE_INSENSITIVE);

    public GuideIntent recognize(String question) {
        if (!StringUtils.hasText(question)) {
            throw new AppException("0001", "问题不能为空");
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        GuideIntent intent = new GuideIntent();
        BigDecimal budgetUpperLimit = recognizeBudgetUpperLimit(normalized);
        intent.setNormalizedQuestion(normalized);
        intent.setBudgetUpperLimit(budgetUpperLimit);
        intent.setBudgetSensitive(budgetUpperLimit != null
                || containsAny(normalized, "预算", "便宜", "性价比", "省钱", "价格", "划算", "低价"));
        intent.setGroupBuyConcerned(containsAny(normalized, "拼团", "成团", "团购"));
        intent.setAfterSaleConcerned(containsAny(normalized, "售后", "退货", "退款", "质保", "保修", "拼团失败", "未成团"));
        intent.setCompareConcerned(containsAny(normalized, "对比", "比较", "哪款", "区别", "更合适", "怎么选", "应该选"));
        intent.setPerformanceSensitive(containsAny(normalized, "剪视频", "剪辑", "绘图", "大型应用", "高刷", "性能", "多任务", "创作", "游戏", "影音", "高配"));
        intent.setPortabilitySensitive(containsAny(normalized, "轻薄", "便携", "携带", "通勤", "宿舍", "课堂", "会议", "键盘"));
        intent.setUserIdentity(containsAny(normalized, "学生", "大学生", "研究生") ? "学生" : "普通用户");
        intent.setUsageScenarios(recognizeScenarios(normalized));
        intent.setOrderId(extractFirst(ORDER_ID_PATTERN, question));
        intent.setGoodsId(extractFirst(GOODS_ID_PATTERN, question));
        intent.setEntities(extractEntities(intent));
        intent.setIntentType(resolveIntentType(intent, normalized));
        return intent;
    }

    private List<String> recognizeScenarios(String normalized) {
        List<String> scenarios = new ArrayList<>();
        if (containsAny(normalized, "论文", "文档", "办公", "会议", "键盘")) {
            scenarios.add("文档写作");
        }
        if (containsAny(normalized, "网课", "学习", "课堂")) {
            scenarios.add("网课学习");
        }
        if (containsAny(normalized, "笔记", "手写")) {
            scenarios.add("手写笔记");
        }
        if (containsAny(normalized, "剪视频", "剪辑", "绘图", "大型应用", "创作", "高配")) {
            scenarios.add("创作应用");
        }
        if (containsAny(normalized, "游戏", "高刷", "影音", "追剧")) {
            scenarios.add("游戏影音");
        }
        if (containsAny(normalized, "儿童", "小孩", "家长", "护眼", "管控")
                && !containsAny(normalized, "不是给小孩", "不是给儿童", "不是儿童", "不给小孩", "不用给小孩")) {
            scenarios.add("儿童学习");
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
        if (isConcreteOrderQuery(intent, normalized)) {
            return GuideIntentType.ORDER_QUERY;
        }
        if (intent.isAfterSaleConcerned()) {
            return GuideIntentType.AFTER_SALE;
        }
        if (intent.isGroupBuyConcerned()) {
            return GuideIntentType.GROUP_RULE;
        }
        if (isTransactionRuleQuestion(normalized)) {
            return GuideIntentType.GROUP_RULE;
        }
        if (intent.isBudgetSensitive() && intent.isPerformanceSensitive()) {
            return GuideIntentType.PRODUCT_COMPARE;
        }
        if (intent.isCompareConcerned()) {
            return GuideIntentType.PRODUCT_COMPARE;
        }
        return GuideIntentType.PRODUCT_RECOMMEND;
    }

    private boolean isConcreteOrderQuery(GuideIntent intent, String normalized) {
        if (!containsAny(normalized, "订单", "支付状态", "物流", "退款状态")) {
            return false;
        }
        if (StringUtils.hasText(intent.getOrderId())) {
            return true;
        }
        return containsAny(normalized, "查订单", "查询订单", "看下订单", "看看订单", "订单状态", "物流到哪");
    }

    private boolean isTransactionRuleQuestion(String normalized) {
        if (normalized.contains("导购回答") && normalized.contains("商品卡片")) {
            return false;
        }
        return containsAny(normalized,
                "活动库存", "库存不足", "名额", "队伍满", "队伍已满", "活动过期",
                "锁单", "支付单", "支付金额", "前端金额", "导购报价凭证", "决策编号",
                "重复下单", "重复推进", "重复通知", "连续点", "确认下单", "生成两个订单", "幂等", "防重放", "补偿", "outbox",
                "结算消息发送失败", "一直卡住");
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

    private List<String> extractEntities(GuideIntent intent) {
        List<String> entities = new ArrayList<>();
        if (StringUtils.hasText(intent.getOrderId())) {
            entities.add("orderId:" + intent.getOrderId());
        }
        if (StringUtils.hasText(intent.getGoodsId())) {
            entities.add("goodsId:" + intent.getGoodsId());
        }
        if (intent.getBudgetUpperLimit() != null) {
            entities.add("budgetUpperLimit:" + intent.getBudgetUpperLimit().toPlainString());
        }
        if (StringUtils.hasText(intent.getUserIdentity())) {
            entities.add("userIdentity:" + intent.getUserIdentity());
        }
        intent.getUsageScenarios().forEach(scenario -> entities.add("scenario:" + scenario));
        return entities;
    }

    private String extractFirst(Pattern pattern, String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : "";
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
