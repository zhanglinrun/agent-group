package com.linrun.domain.academic.runtime.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linrun.trigger.agent.agent.evaluation.AcademicAgentLlmReplanStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * “规则基线 vs LLM 反思重规划”对比评测。
 *
 * <p>在同一份 25 条离线用例（含 2 条失败注入用例）上分别跑：
 * <ul>
 *   <li>规则基线：{@link AgentEvalService#evaluate(List)}（规则关键词匹配的重规划，不调用大模型）；</li>
 *   <li>LLM 反思：注入 {@link AcademicAgentLlmReplanStrategy}，让 LLM 反思后重规划。</li>
 * </ul>
 * 对比执行成功率、重规划恢复数、平均重规划次数等指标，产出对比报告。
 *
 * <p>需要真实 LLM，因此用 {@code DASHSCOPE_API_KEY} 环境变量门控（缺失时跳过，不破坏 CI 的确定性）。
 * 运行方式（需在命令行导出 DASHSCOPE_API_KEY）：
 * <pre>
 * mvn -pl agent-group-app -am test -Dtest=AgentLlmReplanEvalTest
 * </pre>
 * 报告写入 agent-group-app/target/agent-llm-replan-eval.md。
 */
@DisplayName("Agent 重规划策略对比：规则基线 vs LLM 反思")
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class AgentLlmReplanEvalTest {

    private static final String DEFAULT_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private static final String DEFAULT_CHAT_MODEL = "qwen-plus";

    @Test
    void compareRuleBaselineVersusLlmReplan() throws Exception {
        List<AgentEvalCase> cases;
        try (InputStream input = getClass().getResourceAsStream("/evaluation/agent-eval-cases.json")) {
            assertNotNull(input, "评测数据集 evaluation/agent-eval-cases.json 不存在");
            cases = new ObjectMapper().readValue(input, new TypeReference<List<AgentEvalCase>>() {
            });
        }
        assertTrue(cases.size() >= 20, "评测集应至少包含 20 条用例");

        AgentEvalService evalService = new AgentEvalService();

        // 规则基线（确定性的规则关键词匹配重规划，不调用大模型）
        AgentEvalReport baseline = evalService.evaluate(cases);

        // LLM 反思重规划（同一批用例，注入 LLM 策略）
        ChatModel chatModel = buildChatModel();
        AgentEvalReport llm = evalService.evaluate(cases, new AcademicAgentLlmReplanStrategy(chatModel));

        String markdown = buildComparisonMarkdown(cases.size(), baseline, llm);
        Path reportPath = Path.of("target", "agent-llm-replan-eval.md");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, markdown, StandardCharsets.UTF_8);
        System.out.println(markdown);

        // 两套策略都应在注入失败用例上恢复，执行成功率保持 100%
        assertEquals(cases.size(), baseline.getTotalCases(), "规则基线用例数不匹配");
        assertEquals(cases.size(), llm.getTotalCases(), "LLM 评测用例数不匹配");
        assertEquals(1.0D, llm.getFlowSuccessRate(), 1e-9,
                "LLM 反思重规划执行成功率应为 100%（含失败注入后的恢复）");
        assertEquals(baseline.getReplanRecoveredCount(), llm.getReplanRecoveredCount(),
                "LLM 反思重规划恢复数应与规则基线一致：" + baseline.getReplanRecoveredCount());
    }

    private ChatModel buildChatModel() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY").trim();
        String baseUrl = System.getenv().getOrDefault("AGENT_GROUP_LLM_BASE_URL", DEFAULT_BASE_URL);
        String model = System.getenv().getOrDefault("AGENT_GROUP_LLM_CHAT_MODEL", DEFAULT_CHAT_MODEL);
        OpenAiChatOptions options = OpenAiChatOptions.builder().temperature(0.2d).model(model).build();
        // completionsPath 与 application-dev.yml 的 spring.ai.openai.chat.completions-path 保持一致：
        // base-url 已含 /v1，故 completionsPath 去掉 /v1 前缀，避免拼成 .../v1/v1/chat/completions 触发 404。
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .baseUrl(baseUrl)
                        .apiKey(new SimpleApiKey(apiKey))
                        .completionsPath("/chat/completions")
                        .build())
                .defaultOptions(options)
                .build();
    }

    private String buildComparisonMarkdown(int caseCount, AgentEvalReport baseline, AgentEvalReport llm) {
        return """
                # Agent 重规划策略对比评测（规则基线 vs LLM 反思）

                - 评测用例数：%d
                - 评测说明：在同一份离线用例（含失败注入）上分别跑规则基线与 LLM 反思重规划，全程确定性步骤执行；LLM 仅在触发重规划时调用。

                | 指标 | 规则基线 | LLM 反思 |
                |---|---|---|
                | 计划执行成功率 | %.4f | %.4f |
                | 重规划恢复数 | %d | %d |
                | 平均重规划次数 | %.4f | %.4f |
                | 模式选择准确率 | %.4f | %.4f |
                | 总耗时(ms) | %d | %d |
                """.formatted(
                        caseCount,
                        baseline.getFlowSuccessRate(), llm.getFlowSuccessRate(),
                        baseline.getReplanRecoveredCount(), llm.getReplanRecoveredCount(),
                        baseline.getAverageReplanCount(), llm.getAverageReplanCount(),
                        baseline.getModeAccuracy(), llm.getModeAccuracy(),
                        baseline.getTotalElapsedMillis(), llm.getTotalElapsedMillis());
    }
}
