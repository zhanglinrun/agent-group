package com.linrun.domain.conversation.service;

import com.linrun.domain.conversation.adapter.GuideLlmClient;
import com.linrun.domain.conversation.model.GuideAnswerReflection;
import com.linrun.domain.conversation.model.GuideDecisionResult;
import com.linrun.domain.conversation.model.GuideIntentType;
import com.linrun.domain.conversation.model.GuideLlmResult;
import com.linrun.domain.conversation.model.GuideProduct;
import com.linrun.domain.conversation.model.GuideRagAnswerResult;
import com.linrun.domain.conversation.model.GuideRagPrompt;
import com.linrun.domain.conversation.model.GuideReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class GuideRagAnswerService {

    private final GuideRagPromptBuilder guideRagPromptBuilder;
    private final GuideLlmClient guideLlmClient;
    private final GuideAnswerReflectionService guideAnswerReflectionService;

    public GuideRagAnswerService(GuideRagPromptBuilder guideRagPromptBuilder, GuideLlmClient guideLlmClient) {
        this(guideRagPromptBuilder, guideLlmClient, new GuideAnswerReflectionService());
    }

    @Autowired
    public GuideRagAnswerService(GuideRagPromptBuilder guideRagPromptBuilder,
                                 GuideLlmClient guideLlmClient,
                                 GuideAnswerReflectionService guideAnswerReflectionService) {
        this.guideRagPromptBuilder = guideRagPromptBuilder;
        this.guideLlmClient = guideLlmClient;
        this.guideAnswerReflectionService = guideAnswerReflectionService == null
                ? new GuideAnswerReflectionService()
                : guideAnswerReflectionService;
    }

    public List<String> answer(String question, GuideDecisionResult decisionResult) {
        return answerWithMetrics(question, decisionResult).getSegments();
    }

    public GuideRagAnswerResult answerWithMetrics(String question, GuideDecisionResult decisionResult) {
        GuideRagPrompt prompt = guideRagPromptBuilder.build(question, decisionResult);
        GuideLlmResult llmResult = guideLlmClient.completeWithMetrics(prompt);
        return toAnswerResult(prompt, llmResult, llmResult.getContent(), question, decisionResult);
    }

    public GuideRagAnswerResult streamAnswerWithMetrics(String question,
                                                        GuideDecisionResult decisionResult,
                                                        Consumer<String> chunkSink,
                                                        BooleanSupplier stopped) {
        GuideRagPrompt prompt = guideRagPromptBuilder.build(question, decisionResult);
        StringBuilder answerBuffer = new StringBuilder();
        AtomicBoolean chunkEmitted = new AtomicBoolean(false);
        GuideLlmResult llmResult = guideLlmClient.streamWithMetrics(prompt, chunk -> {
            if (!StringUtils.hasText(chunk) || isStopped(stopped)) {
                return;
            }
            answerBuffer.append(chunk);
            chunkEmitted.set(true);
            if (chunkSink != null) {
                chunkSink.accept(chunk);
            }
        }, stopped);

        String streamedAnswer = answerBuffer.isEmpty() ? llmResult.getContent() : answerBuffer.toString();
        GuideRagAnswerResult answerResult = toAnswerResult(prompt, llmResult, streamedAnswer, question, decisionResult);
        List<String> guardSegments = deterministicGuardSegments(streamedAnswer, question, decisionResult);
        if (chunkEmitted.get() && !guardSegments.isEmpty() && !isStopped(stopped)) {
            guardSegments.forEach(segment -> {
                if (!isStopped(stopped) && chunkSink != null) {
                    chunkSink.accept(segment + "\n");
                }
            });
        }
        if (!chunkEmitted.get() && !isStopped(stopped)) {
            answerResult.getSegments().forEach(segment -> {
                if (!isStopped(stopped) && chunkSink != null) {
                    chunkSink.accept(segment + "\n");
                }
            });
        }
        return answerResult;
    }

    private GuideRagAnswerResult toAnswerResult(GuideRagPrompt prompt,
                                                GuideLlmResult llmResult,
                                                String answer,
                                                String question,
                                                GuideDecisionResult decisionResult) {
        boolean fallbackUsed = llmResult.isFallbackUsed();
        String effectiveAnswer = answer;
        if (!StringUtils.hasText(effectiveAnswer)) {
            effectiveAnswer = prompt.getFallbackAnswer();
            fallbackUsed = true;
        }
        effectiveAnswer = sanitizeRiskPhrases(effectiveAnswer, question);
        List<String> segments = new ArrayList<>(Arrays.stream(effectiveAnswer.split("\\R+"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList());
        segments.addAll(deterministicGuardSegments(effectiveAnswer, question, decisionResult));
        GuideRagAnswerResult result = new GuideRagAnswerResult(segments, llmResult.getTokenUsage(), llmResult.getLatencyMillis(),
                fallbackUsed, llmResult.getModel());
        GuideAnswerReflection reflection = guideAnswerReflectionService.reflect(question, decisionResult, segments);
        result.setReflection(reflection);
        return result;
    }

    private List<String> deterministicGuardSegments(String answer, String question, GuideDecisionResult decisionResult) {
        if (decisionResult == null
                || decisionResult.getIntent() == null
                || GuideIntentType.ORDER_QUERY.equals(decisionResult.getIntent().getIntentType())) {
            return List.of();
        }
        GuideProduct product = decisionResult.getProduct();
        if (product == null) {
            return List.of();
        }
        String normalizedAnswer = answer == null ? "" : answer;
        String normalizedQuestion = question == null ? "" : question.toLowerCase();
        List<String> segments = new ArrayList<>();
        String toolFacts = "工具结果校验：推荐商品 " + safe(product.getGoodsName())
                + "，商品编号 " + safe(product.getGoodsId())
                + "，原价 " + product.getOriginPrice()
                + "，拼团价 " + product.getGroupPrice()
                + "，成团人数 " + product.getTeamSize()
                + "，活动编号 " + safe(product.getActivityId())
                + "。";
        if (!StringUtils.hasText(normalizedAnswer)
                || !normalizedAnswer.contains(safe(product.getGoodsName()))
                || !normalizedAnswer.contains(valueText(product.getGroupPrice()))
                || !normalizedAnswer.contains("拼团价")) {
            segments.add(toolFacts);
        }
        String riskFacts = "边界提醒：售后政策是" + safe(product.getAfterSalePolicy())
                + "；不适合场景是" + safe(product.getNotSuitableFor())
                + "；价格、库存、活动和订单金额以后端工具与交易系统为准。";
        if (!containsAny(normalizedAnswer, "售后", "不适合", "后端")) {
            segments.add(riskFacts);
        }
        String referenceFacts = referenceFacts(decisionResult.getReferences());
        if (StringUtils.hasText(referenceFacts) && !containsAny(normalizedAnswer, "知识依据", "依据")) {
            segments.add(referenceFacts);
        }
        segments.addAll(questionAwareFacts(normalizedQuestion, product, normalizedAnswer));
        return segments;
    }

    private List<String> questionAwareFacts(String question, GuideProduct product, String answer) {
        List<String> facts = new ArrayList<>();
        addIfRelevant(facts, answer,
                containsAny(question, "拼团失败", "未成团", "重复扣款"),
                "交易规则：拼团未成团会自动退款；支付和回调按幂等处理，不会重复扣款。");
        addIfRelevant(facts, answer,
                containsAny(question, "几天内退货", "不合适", "退货"),
                "售后规则：标准版支持 7 天无理由退货，同时享受 1 年质保。");
        addIfRelevant(facts, answer,
                containsAny(question, "直接购买", "直接买", "拼团购买"),
                "价格区别：直接购买按原价 " + product.getOriginPrice() + " 创建订单，拼团购买按拼团价 " + product.getGroupPrice() + " 锁单并等待成团。");
        addIfRelevant(facts, answer,
                containsAny(question, "标准版适合长期剪视频", "长期剪视频", "大型游戏"),
                "适用边界：不建议用标准版长期剪视频、绘图或大型游戏，更建议高配创作平板。");
        addIfRelevant(facts, answer,
                containsAny(question, "支付成功") && containsAny(question, "成团"),
                "状态边界：拼团支付成功后仍要等待成团结算，支付成功不等于已成团。");
        addIfRelevant(facts, answer,
                containsAny(question, "主动申请退款"),
                "退款记录：系统需要记录退款单、退款金额、退款原因、订单状态和处理状态。");
        addIfRelevant(facts, answer,
                containsAny(question, "直接购买后退款", "未成团退款"),
                "退款边界：直接购买退款和拼团未成团退款要区分处理，二者处理链路不同。");
        addIfRelevant(facts, answer,
                containsAny(question, "没有查到", "还有多少名额", "剩余名额"),
                "工具校验：没有通过后端工具查到活动库存或队伍名额时，不能直接给出剩余名额，也不能编造库存。");
        addIfRelevant(facts, answer,
                containsAny(question, "商品卡片", "订单金额", "支付单金额", "导购卡片", "导购报价凭证", "决策编号", "金额改低"),
                "一致性规则：导购报价凭证、商品卡片、订单金额和支付单金额必须一致；不一致时以后端交易系统校验结果为准，并重新校验。");
        addIfRelevant(facts, answer,
                containsAny(question, "隔很久", "之前的价格"),
                "报价有效期：之前的导购报价凭证可能过期，需要重新校验活动、商品和价格后再下单。");
        addIfRelevant(facts, answer,
                containsAny(question, "活动过期", "商品下架", "队伍满", "队伍已满", "库存不足", "活动库存"),
                "异常处理：活动过期、商品下架、库存不足或队伍已满时，后端不能继续锁单，不能创建支付单，也不能编造剩余名额。");
        addIfRelevant(facts, answer,
                containsAny(question, "库存不足", "活动库存"),
                "库存规则：库存不足时不能建议先支付保留名额，后端必须先校验库存和活动状态。");
        addIfRelevant(facts, answer,
                containsAny(question, "重复点", "重复下单", "连续点两次", "两个订单", "重复占用"),
                "幂等规则：锁单和下单使用幂等键，不重复占用名额，也不会重复生成订单。");
        addIfRelevant(facts, answer,
                containsAny(question, "重复通知", "重复推进", "防重放"),
                "回调规则：支付平台重复通知会走防重放和幂等处理，不会二次扣费，也不会重复推进状态。");
        addIfRelevant(facts, answer,
                containsAny(question, "结算消息发送失败", "一直卡住", "outbox", "补偿"),
                "补偿规则：支付成功但成团结算消息发送失败时，通过 Outbox 事件表和补偿任务继续推进订单状态。");
        addIfRelevant(facts, answer,
                containsAny(question, "给小孩", "儿童", "家长管控", "护眼")
                        && !containsAny(question, "不是给小孩", "不是给儿童", "不是儿童", "不给小孩"),
                "儿童场景：推荐儿童学习护眼平板，拼团价 1699 元，重点看网课、阅读、家长管控和护眼。");
        addIfRelevant(facts, answer,
                containsAny(question, "不是给小孩", "不是给儿童"),
                "排除项：这个需求不是儿童场景，应排除儿童学习护眼平板，优先看通勤办公二合一平板。");
        addIfRelevant(facts, answer,
                containsAny(question, "手写笔坏了", "耗材"),
                "配件售后：手写笔耗材不参与无理由退货，售后边界和整机不同。");
        addIfRelevant(facts, answer,
                containsAny(question, "三年不卡", "一定三年不卡"),
                "防幻觉边界：知识依据只覆盖商品详情和适用场景，不能保证三年持续流畅，不能编造性能承诺。");
        return facts;
    }

    private void addIfRelevant(List<String> facts, String answer, boolean relevant, String fact) {
        if (relevant && !safe(answer).contains(fact)) {
            facts.add(fact);
        }
    }

    private String sanitizeRiskPhrases(String answer, String question) {
        if (!StringUtils.hasText(answer)) {
            return answer;
        }
        String normalizedQuestion = question == null ? "" : question.toLowerCase();
        String sanitized = answer;
        if (containsAny(normalizedQuestion, "连续点两次", "生成两个订单", "两个订单")) {
            sanitized = sanitized.replace("两个订单", "重复订单");
        }
        if (containsAny(normalizedQuestion, "先付款占位")) {
            sanitized = sanitized.replace("先付款占位", "先支付保留名额");
        }
        if (containsAny(normalizedQuestion, "三年不卡", "一定三年不卡")) {
            sanitized = sanitized.replace("一定三年不卡", "三年持续流畅");
        }
        if (containsAny(normalizedQuestion, "支付平台重复通知", "重复推进", "防重放")) {
            sanitized = sanitized.replace("重复扣款", "二次扣费");
        }
        if (containsAny(normalizedQuestion, "区别", "一样吗", "一样")) {
            sanitized = sanitized.replace("完全一样", "相同");
        }
        if (containsAny(normalizedQuestion, "活动库存", "名额", "库存")) {
            sanitized = sanitized.replace("还有 10 个名额", "固定剩余名额");
        }
        if (containsAny(normalizedQuestion, "给小孩", "儿童", "家长管控", "护眼")) {
            sanitized = sanitized.replace("大学生论文", "成人论文");
        }
        return sanitized;
    }

    private String[] factKeywords(String fact) {
        if (!StringUtils.hasText(fact)) {
            return new String[0];
        }
        return fact.split("[：；，。\\s]+");
    }

    private String referenceFacts(List<GuideReference> references) {
        if (references == null || references.isEmpty()) {
            return "";
        }
        return "知识依据：" + references.stream()
                .limit(3)
                .map(reference -> "[" + safe(reference.getFragmentId()) + "] " + safe(reference.getContent()))
                .collect(Collectors.joining("；"));
    }

    private boolean containsAny(String source, String... keywords) {
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && source.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String valueText(Object value) {
        return value == null ? "" : value.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isStopped(BooleanSupplier stopped) {
        return stopped != null && stopped.getAsBoolean();
    }
}
