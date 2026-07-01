package com.linrun.domain.agent.runtime.tool.common;

import com.linrun.domain.agent.runtime.tool.AgentToolCallCommand;
import com.linrun.domain.agent.runtime.tool.AgentToolCallResult;
import com.linrun.domain.agent.runtime.tool.AgentToolRuntimeRegistry;
import com.linrun.domain.agent.runtime.tool.output.AgentToolOutputNames;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentReportToolRuntimeTest {

    @Test
    void shouldBuildMarkdownReport() {
        AgentReportToolRuntime reportTool = new AgentReportToolRuntime();
        AgentToolRuntimeRegistry registry = new AgentToolRuntimeRegistry();
        registry.registerStructured(AgentReportToolRuntime.definition(), reportTool::call);

        AgentToolCallResult result = registry.call(AgentToolCallCommand.builder(AgentToolOutputNames.REPORT_TOOL)
                .arguments(Map.of(
                        "title", "论文实验指标分析报告",
                        "summary", "不同方法在同一数据集上的准确率存在差异。",
                        "sections", List.of(
                                Map.of("heading", "实验设置", "content", "系统一比较同一数据集上的指标。"),
                                Map.of("heading", "结果分析", "content", "补充消融实验说明指标差异。")),
                        "evidence", List.of("实验指标来自 experiment_result 行")))
                .build());

        assertTrue(result.isSuccess());
        assertEquals("论文实验指标分析报告", result.getResult().get("title"));
        String content = String.valueOf(result.getResult().get("content"));
        assertTrue(content.contains("# 论文实验指标分析报告"));
        assertTrue(content.contains("## 实验设置"));
        assertTrue(content.contains("- 实验指标来自 experiment_result 行"));
    }
}















