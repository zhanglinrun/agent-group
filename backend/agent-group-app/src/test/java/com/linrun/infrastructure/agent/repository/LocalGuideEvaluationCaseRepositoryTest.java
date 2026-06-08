package com.linrun.infrastructure.agent.repository;

import com.linrun.domain.agent.quality.model.GuideEvaluationCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalGuideEvaluationCaseRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldProvideThirtyTypicalEvaluationCases() {
        LocalGuideEvaluationCaseRepository repository = new LocalGuideEvaluationCaseRepository();

        List<GuideEvaluationCase> cases = repository.queryEnabledCases();

        assertEquals(30, cases.size());
        assertTrue(cases.stream().anyMatch(GuideEvaluationCase::isContextRequired));
        assertTrue(cases.stream().anyMatch(evaluationCase -> "EV10030".equals(evaluationCase.getCaseId())));
        assertTrue(cases.stream().allMatch(evaluationCase -> evaluationCase.getExpectedGoodsId().startsWith("G")));
        assertTrue(cases.stream().allMatch(evaluationCase -> !evaluationCase.getExpectedToolOrder().isEmpty()));
    }

    @Test
    void shouldLoadEvaluationCasesFromJsonFile() throws Exception {
        Path caseFile = tempDir.resolve("quota-eval-cases.json");
        Files.writeString(caseFile, """
                [
                  {
                    "caseId": "EV-CUSTOM-001",
                    "caseName": "自定义预算用例",
                    "question": "预算有限，想买基础额度包",
                    "expectedIntentType": "PRODUCT_RECOMMEND",
                    "expectedGoodsId": "G10001",
                    "contextRequired": false,
                    "requiredReferenceKeywords": ["额度"],
                    "requiredAnswerKeywords": ["拼团价"]
                  }
                ]
                """);
        LocalGuideEvaluationCaseRepository repository = new LocalGuideEvaluationCaseRepository(caseFile.toString());

        List<GuideEvaluationCase> cases = repository.queryEnabledCases();

        assertEquals(1, cases.size());
        assertEquals("EV-CUSTOM-001", cases.get(0).getCaseId());
        assertEquals(List.of("额度"), cases.get(0).getRequiredReferenceKeywords());
    }
}
