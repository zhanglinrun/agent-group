package com.linrun.domain.academic.runtime.diagnosis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异常诊断服务测试。
 */
class AgentDiagnosisServiceTest {

    private final AgentDiagnosisService service = new AgentDiagnosisService();

    @Test
    void testDiagnose_Normal() {
        var context = new AgentDiagnosisService.AgentRunContext(
                "run-1", 5000, 0, 10.0, 0, false, null
        );

        var report = service.diagnose(context);

        assertEquals(AgentDiagnosisService.DiagnosisLevel.OK, report.getLevel());
        assertFalse(report.hasIssues());
    }

    @Test
    void testDiagnose_SlowExecution() {
        var context = new AgentDiagnosisService.AgentRunContext(
                "run-2", 40000, 0, 10.0, 0, false, null
        );

        var report = service.diagnose(context);

        assertEquals(AgentDiagnosisService.DiagnosisLevel.WARNING, report.getLevel());
        assertTrue(report.hasIssues());
    }

    @Test
    void testDiagnose_ToolFailure() {
        var context = new AgentDiagnosisService.AgentRunContext(
                "run-3", 5000, 2, 10.0, 0, false, null
        );

        var report = service.diagnose(context);

        assertEquals(AgentDiagnosisService.DiagnosisLevel.ERROR, report.getLevel());
        assertEquals(1, report.getIssues().size());
    }

    @Test
    void testDiagnose_FrequentReplan() {
        var context = new AgentDiagnosisService.AgentRunContext(
                "run-4", 5000, 0, 10.0, 5, false, null
        );

        var report = service.diagnose(context);

        assertEquals(AgentDiagnosisService.DiagnosisLevel.WARNING, report.getLevel());
        assertTrue(report.getSummary().contains("重规划次数过多"));
    }

    @Test
    void testDiagnose_Exception() {
        var context = new AgentDiagnosisService.AgentRunContext(
                "run-5", 5000, 0, 10.0, 0, true, "NullPointerException"
        );

        var report = service.diagnose(context);

        assertEquals(AgentDiagnosisService.DiagnosisLevel.ERROR, report.getLevel());
        assertTrue(report.getSummary().contains("异常"));
    }
}
