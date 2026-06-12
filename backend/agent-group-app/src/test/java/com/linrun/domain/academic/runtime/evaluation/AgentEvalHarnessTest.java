package com.linrun.domain.academic.runtime.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 引擎离线评测入口。
 *
 * 运行方式：mvn -pl agent-group-app -am test -Dtest=AgentEvalHarnessTest
 * 评测报告会输出到控制台，并写入 agent-group-app/target/agent-eval-report.md。
 */
class AgentEvalHarnessTest {

    @Test
    void runOfflineEvalSuiteAndWriteReport() throws Exception {
        List<AgentEvalCase> cases;
        try (InputStream input = getClass().getResourceAsStream("/evaluation/agent-eval-cases.json")) {
            assertNotNull(input, "评测数据集 evaluation/agent-eval-cases.json 不存在");
            cases = new ObjectMapper().readValue(input, new TypeReference<List<AgentEvalCase>>() {
            });
        }
        assertTrue(cases.size() >= 20, "评测集应至少包含 20 条用例");

        AgentEvalReport report = new AgentEvalService().evaluate(cases);

        Path reportPath = Path.of("target", "agent-eval-report.md");
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report.toMarkdown(), StandardCharsets.UTF_8);
        System.out.println(report.toMarkdown());

        assertEquals(cases.size(), report.getTotalCases());
        assertTrue(report.getModeAccuracy() >= 0.9,
                "模式选择准确率低于 90%：" + report.getModeAccuracy());
        assertEquals(1.0D, report.getFlowSuccessRate(), 1e-9,
                "计划执行成功率应为 100%（含失败注入后的重规划恢复）");
        assertEquals(2, report.getReplanRecoveredCount(),
                "两条失败注入用例都应通过重规划恢复");
    }
}
