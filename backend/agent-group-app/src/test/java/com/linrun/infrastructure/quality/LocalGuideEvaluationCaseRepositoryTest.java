package com.linrun.infrastructure.quality;

import com.linrun.domain.quality.model.GuideEvaluationCase;
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
    void shouldProvideTwentyTypicalGuideEvaluationCases() {
        LocalGuideEvaluationCaseRepository repository = new LocalGuideEvaluationCaseRepository();

        List<GuideEvaluationCase> cases = repository.queryEnabledCases();

        assertEquals(20, cases.size());
        assertTrue(cases.stream().anyMatch(GuideEvaluationCase::isContextRequired));
        assertTrue(cases.stream().allMatch(evaluationCase -> "G10001".equals(evaluationCase.getExpectedGoodsId())));
    }

    @Test
    void shouldLoadEvaluationCasesFromJsonFile() throws Exception {
        Path caseFile = tempDir.resolve("guide-eval-cases.json");
        Files.writeString(caseFile, """
                [
                  {
                    "caseId": "EV-CUSTOM-001",
                    "caseName": "自定义预算用例",
                    "question": "预算 2500 以内买学习平板",
                    "expectedIntentType": "PRODUCT_RECOMMEND",
                    "expectedGoodsId": "G10001",
                    "contextRequired": false,
                    "requiredReferenceKeywords": ["学习"],
                    "requiredAnswerKeywords": ["拼团价"]
                  }
                ]
                """);
        LocalGuideEvaluationCaseRepository repository = new LocalGuideEvaluationCaseRepository(caseFile.toString());

        List<GuideEvaluationCase> cases = repository.queryEnabledCases();

        assertEquals(1, cases.size());
        assertEquals("EV-CUSTOM-001", cases.get(0).getCaseId());
        assertEquals(List.of("学习"), cases.get(0).getRequiredReferenceKeywords());
    }
}
