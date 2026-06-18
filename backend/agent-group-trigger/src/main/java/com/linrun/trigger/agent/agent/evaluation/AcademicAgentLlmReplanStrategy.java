package com.linrun.trigger.agent.agent.evaluation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.linrun.domain.academic.runtime.agent.AcademicAgentFlowReplanRequest;
import com.linrun.domain.academic.runtime.agent.AcademicAgentReplanStrategy;
import com.linrun.domain.academic.runtime.agent.AcademicAgentStepExecutionResult;
import com.linrun.domain.academic.runtime.agent.AcademicPlanStep;
import com.linrun.domain.academic.runtime.reasoning.AcademicAgentIntelligentReplanStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * LLM 驱动的反思重规划策略（规则为基线）。
 *
 * <p>规则基线 {@link AcademicAgentIntelligentReplanStrategy} 用关键词匹配判断失败类型，
 * 只能覆盖 "tool not found / parameter invalid / timeout" 这几类预设场景，
 * 遇到陌生失败描述会一律判为不可恢复。这里在它之上叠加一次 LLM 反思：
 * <ol>
 *   <li>把失败步骤、失败信息、已完成步骤、重规划次数整理成提示，让 LLM 输出结构化反思
 *       {@link LlmReplanReflection}（是否可恢复 + 失败分析 + 修订后的剩余步骤）；</li>
 *   <li>LLM 判定可恢复且给出步骤时，按线性依赖重建 {@link AcademicPlanStep}；</li>
 *   <li>LLM 不可用、判定不可恢复、或返回空步骤时，全部优雅降级到规则基线，保证不拖垮执行链路。</li>
 * </ol>
 *
 * <p>策略放在 trigger 模块，直接复用生产 PlanExecuteAgent 已在用的
 * {@code ChatClient} + {@code BeanOutputConverter} 结构化输出模式；domain 模块保持无 LLM 依赖。
 */
public class AcademicAgentLlmReplanStrategy implements AcademicAgentReplanStrategy {

    private static final Logger log = LoggerFactory.getLogger(AcademicAgentLlmReplanStrategy.class);
    private static final Pattern THINK_PATTERN = Pattern.compile("(?s)<think>.*?</think>");

    private final ChatClient chatClient;
    private final AcademicAgentReplanStrategy fallback;

    public AcademicAgentLlmReplanStrategy(ChatModel chatModel) {
        this(chatModel, new AcademicAgentIntelligentReplanStrategy());
    }

    public AcademicAgentLlmReplanStrategy(ChatModel chatModel, AcademicAgentReplanStrategy fallback) {
        this.chatClient = chatModel != null ? ChatClient.builder(chatModel).build() : null;
        this.fallback = fallback != null ? fallback : new AcademicAgentIntelligentReplanStrategy();
    }

    @Override
    public List<AcademicPlanStep> replan(AcademicAgentFlowReplanRequest request) {
        if (request == null || request.failedStep() == null || chatClient == null) {
            return fallback.replan(request);
        }
        try {
            LlmReplanReflection reflection = callLlm(request);
            if (reflection == null) {
                log.warn("[LlmReplan] LLM 未返回有效反思，降级到规则基线");
                return fallback.replan(request);
            }
            if (!reflection.recoverable()) {
                log.info("[LlmReplan] LLM 判定不可恢复({})，降级兜底", reflection.failureAnalysis());
                return fallback.replan(request);
            }
            List<AcademicPlanStep> rebuilt = rebuildSteps(request, reflection);
            if (rebuilt.isEmpty()) {
                log.warn("[LlmReplan] LLM 反思未产出可用步骤，降级兜底");
                return fallback.replan(request);
            }
            log.info("[LlmReplan] LLM 反思恢复成功: {} 条新步骤, 原因={}", rebuilt.size(), reflection.failureAnalysis());
            return rebuilt;
        } catch (Exception e) {
            log.warn("[LlmReplan] LLM 反思调用失败，降级到规则基线: {}", e.getMessage());
            return fallback.replan(request);
        }
    }

    private LlmReplanReflection callLlm(AcademicAgentFlowReplanRequest request) {
        BeanOutputConverter<LlmReplanReflection> converter =
                new BeanOutputConverter<>(new ParameterizedTypeReference<>() {});
        String prompt = buildPrompt(request, converter.getFormat());
        String raw = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        return converter.convert(stripThinkAndFences(raw));
    }

    private String buildPrompt(AcademicAgentFlowReplanRequest request, String format) {
        AcademicAgentStepExecutionResult failedResult = request.failedResult();
        String failureNote = failedResult != null ? failedResult.note() : "未知错误";
        AcademicPlanStep failedStep = request.failedStep();
        List<AcademicPlanStep> completed = request.completedSteps();
        StringBuilder completedText = new StringBuilder();
        if (completed != null) {
            for (AcademicPlanStep step : completed) {
                if (step == null) {
                    continue;
                }
                completedText.append("- 第").append(step.getOrder()).append("步: ")
                        .append(step.getInstruction()).append('\n');
            }
        }
        return """
                你是一个 Agent 执行链路的事后反思与重规划助手。
                某个多步骤计划在执行中某一步失败了，请分析失败原因并产出修订后的剩余执行步骤。

                ## 失败步骤
                第 %d 步: %s
                失败信息: %s

                ## 已成功完成的步骤
                %s

                ## 当前已重规划次数
                %d

                ## 要求
                1. 判断这次失败是否可以通过调整剩余步骤来恢复。工具缺失、参数错误、超时、临时错误通常可恢复；任务本质不可达成则不可恢复。
                2. failureAnalysis 给出简洁的失败原因。
                3. 可恢复时，revisedSteps 给出从失败步骤开始的剩余步骤指令列表，每条是独立的可执行指令，用中文，覆盖失败步骤及其后续未完成部分；不可恢复时留空数组。

                仅输出符合如下 JSON Schema 的 JSON，不要输出多余解释或 markdown 代码块标记:
                %s
                """.formatted(
                        failedStep != null ? failedStep.getOrder() : -1,
                        failedStep != null ? failedStep.getInstruction() : "(未知步骤)",
                        failureNote,
                        completedText.length() == 0 ? "(无)" : completedText.toString(),
                        request.replanCount(),
                        format);
    }

    private List<AcademicPlanStep> rebuildSteps(AcademicAgentFlowReplanRequest request,
                                                LlmReplanReflection reflection) {
        AcademicPlanStep failedStep = request.failedStep();
        int startOrder = failedStep != null ? failedStep.getOrder() : 1;
        String baseId = failedStep != null && StringUtils.hasText(failedStep.getStepId())
                ? failedStep.getStepId() : "step";
        String assignedAgent = failedStep != null ? failedStep.getAssignedAgent() : "executor";

        List<AcademicPlanStep> rebuilt = new ArrayList<>();
        int order = startOrder;
        String prevId = null;
        int replanRound = request.replanCount() + 1;
        List<String> instructions = reflection.revisedSteps();
        for (int i = 0; i < instructions.size(); i++) {
            String instruction = instructions.get(i);
            if (!StringUtils.hasText(instruction)) {
                continue;
            }
            String stepId = baseId + "_llm_" + replanRound + "_" + (i + 1);
            List<String> deps = prevId != null ? List.of(prevId) : List.of();
            rebuilt.add(AcademicPlanStep.builder(stepId, instruction)
                    .order(order)
                    .assignedAgent(assignedAgent)
                    .dependencies(deps)
                    .build());
            prevId = stepId;
            order++;
        }
        return rebuilt;
    }

    /**
     * 剔除推理模型常见的 {@code <think>...</think>} 标签与 markdown 代码块围栏，
     * 只保留 JSON 主体交给 {@link BeanOutputConverter} 解析。
     */
    private static String stripThinkAndFences(String raw) {
        String s = THINK_PATTERN.matcher(raw).replaceAll("").trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline > 0) {
                s = s.substring(firstNewline + 1);
            }
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
        }
        return s.trim();
    }

    /**
     * LLM 反思的结构化输出。
     *
     * @param recoverable     失败是否可通过调整剩余步骤恢复
     * @param failureAnalysis 失败原因分析
     * @param revisedSteps    可恢复时，从失败步骤开始的剩余步骤指令列表
     */
    public record LlmReplanReflection(
            @JsonProperty("recoverable") boolean recoverable,
            @JsonProperty("failureAnalysis") String failureAnalysis,
            @JsonProperty("revisedSteps") List<String> revisedSteps) {

        public LlmReplanReflection {
            if (failureAnalysis == null) {
                failureAnalysis = "";
            }
            if (revisedSteps == null) {
                revisedSteps = List.of();
            }
        }
    }
}
