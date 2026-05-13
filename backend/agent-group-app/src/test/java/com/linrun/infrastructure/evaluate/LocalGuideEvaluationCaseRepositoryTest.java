package com.linrun.infrastructure.evaluate;

import com.linrun.domain.evaluate.model.GuideEvaluationCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalGuideEvaluationCaseRepositoryTest {

    @Test
    void shouldProvideTwentyTypicalGuideEvaluationCases() {
        LocalGuideEvaluationCaseRepository repository = new LocalGuideEvaluationCaseRepository();

        List<GuideEvaluationCase> cases = repository.queryEnabledCases();

        assertEquals(20, cases.size());
        assertTrue(cases.stream().anyMatch(GuideEvaluationCase::isContextRequired));
        assertTrue(cases.stream().allMatch(evaluationCase -> "G10001".equals(evaluationCase.getExpectedGoodsId())));
    }
}
