package com.linrun.domain.academic.runtime.tool.common;

import com.linrun.domain.academic.runtime.tool.AcademicToolCallCommand;
import com.linrun.domain.academic.runtime.tool.AcademicToolCallResult;
import com.linrun.domain.academic.runtime.tool.AcademicToolRuntimeRegistry;
import com.linrun.domain.academic.runtime.tool.output.AcademicToolOutputNames;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AcademicReportToolRuntimeTest {

    @Test
    void shouldBuildMarkdownReport() {
        AcademicReportToolRuntime reportTool = new AcademicReportToolRuntime();
        AcademicToolRuntimeRegistry registry = new AcademicToolRuntimeRegistry();
        registry.registerStructured(AcademicReportToolRuntime.definition(), reportTool::call);

        AcademicToolCallResult result = registry.call(AcademicToolCallCommand.builder(AcademicToolOutputNames.REPORT_TOOL)
                .arguments(Map.of(
                        "title", "拼团交易一致性报告",
                        "summary", "支付成功不等于额度到账。",
                        "sections", List.of(
                                Map.of("heading", "交易规则", "content", "成团后才能发放额度。"),
                                Map.of("heading", "补偿策略", "content", "退款后需要回滚额度。")),
                        "evidence", List.of("订单状态来自后端交易系统")))
                .build());

        assertTrue(result.isSuccess());
        assertEquals("拼团交易一致性报告", result.getResult().get("title"));
        String content = String.valueOf(result.getResult().get("content"));
        assertTrue(content.contains("# 拼团交易一致性报告"));
        assertTrue(content.contains("## 交易规则"));
        assertTrue(content.contains("- 订单状态来自后端交易系统"));
    }
}
