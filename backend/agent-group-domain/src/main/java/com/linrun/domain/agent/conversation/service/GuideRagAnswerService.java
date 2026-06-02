package com.linrun.domain.agent.conversation.service;

import com.linrun.domain.agent.conversation.adapter.GuideLlmClient;
import com.linrun.domain.agent.conversation.model.GuideAnswerReflection;
import com.linrun.domain.agent.conversation.model.GuideDecisionResult;
import com.linrun.domain.agent.conversation.model.GuideIntentType;
import com.linrun.domain.agent.conversation.model.GuideLlmResult;
import com.linrun.domain.agent.conversation.model.GuideProduct;
import com.linrun.domain.agent.conversation.model.GuideRagAnswerResult;
import com.linrun.domain.agent.conversation.model.GuideRagPrompt;
import com.linrun.domain.agent.conversation.model.GuideReference;
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
        String toolFacts = "工具结果校验：推荐额度包 " + safe(product.getGoodsName())
                + "，额度包编号 " + safe(product.getGoodsId())
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
        String riskFacts = "边界提醒：退款规则是" + safe(product.getAfterSalePolicy())
                + "；不适合场景是" + safe(product.getNotSuitableFor())
                + "；价格、名额、活动和订单金额以后端工具与交易系统为准。";
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
                containsAny(question, "退款", "退货", "不用了", "额度回滚"),
                "退款规则：额度属于虚拟权益，已使用部分不能退款；未使用额度退款时需要同步回滚额度账户。");
        addIfRelevant(facts, answer,
                containsAny(question, "直接购买", "直接买", "拼团购买"),
                "价格区别：直接购买按原价 " + product.getOriginPrice() + " 创建订单，拼团购买按拼团价 " + product.getGroupPrice() + " 锁单并等待成团。");
        addIfRelevant(facts, answer,
                containsAny(question, "普通问答", "摘要", "轻量"),
                "适用建议：轻量学术问答、论文摘要和资料整理优先选择基础额度包，避免一次性购买过多额度。");
        addIfRelevant(facts, answer,
                containsAny(question, "论文", "文献", "pdf", "精读"),
                "适用建议：论文阅读类任务通常需要上传文件、生成精读笔记和复现清单，优先选择论文阅读额度包。");
        addIfRelevant(facts, answer,
                containsAny(question, "ppt", "汇报", "答辩", "组会"),
                "适用建议：PPT 创作类任务会消耗更多生成额度，优先选择 PPT 创作额度包。");
        addIfRelevant(facts, answer,
                containsAny(question, "图表", "流程图", "架构图", "mermaid"),
                "适用建议：图表重建类任务优先选择图表重建额度包，便于生成可编辑结构化草稿。");
        addIfRelevant(facts, answer,
                containsAny(question, "深度研究", "调研", "长报告", "技术路线"),
                "适用建议：深度研究、复杂主题拆解和长报告生成优先选择深度研究额度包。");
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
                containsAny(question, "订单金额", "支付单金额", "前端金额", "金额改低", "价格篡改"),
                "一致性规则：额度包价格、活动价格、订单金额和支付单金额必须由后端交易系统统一校验；不一致时以后端校验结果为准。");
        addIfRelevant(facts, answer,
                containsAny(question, "隔很久", "之前的价格"),
                "价格有效期：之前看到的活动价可能已经变化，下单前需要重新校验活动、额度包和价格。");
        addIfRelevant(facts, answer,
                containsAny(question, "活动过期", "额度包下架", "队伍满", "队伍已满", "库存不足", "活动库存"),
                "异常处理：活动过期、额度包下架、库存不足或队伍已满时，后端不能继续锁单，不能创建支付单，也不能编造剩余名额。");
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
                containsAny(question, "额度不够", "余额不足", "消耗完"),
                "额度规则：使用 Agent 前会校验额度余额，余额不足时需要先购买或拼团购买额度包。");
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
        if (containsAny(normalizedQuestion, "支付平台重复通知", "重复推进", "防重放")) {
            sanitized = sanitized.replace("重复扣款", "二次扣费");
        }
        if (containsAny(normalizedQuestion, "区别", "一样吗", "一样")) {
            sanitized = sanitized.replace("完全一样", "相同");
        }
        if (containsAny(normalizedQuestion, "活动库存", "名额", "库存")) {
            sanitized = sanitized.replace("还有 10 个名额", "固定剩余名额");
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
