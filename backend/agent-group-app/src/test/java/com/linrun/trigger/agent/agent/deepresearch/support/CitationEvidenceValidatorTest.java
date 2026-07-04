package com.linrun.trigger.agent.agent.deepresearch.support;

import com.linrun.trigger.agent.entity.record.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CitationEvidenceValidatorTest {

    @Test
    void rejectsTaskCitationInSummary() {
        CitationEvidenceValidator.ValidationResult result = CitationEvidenceValidator.validateSummary(
                "依据 task-1 和 task-7 可确认 ICASSP 2024 共 3 篇。",
                List.of(new SearchResult("https://example.com", "demo", "content")));

        assertFalse(result.passed());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("task-N")));
    }

    @Test
    void rejectsDoiWithoutReferences() {
        CitationEvidenceValidator.ValidationResult result = CitationEvidenceValidator.validateSummary(
                "DOI: 10.1109/GLOBECOM46510.2024.10786990 显示准确率 92.3%。",
                List.of());

        assertFalse(result.passed());
    }

    @Test
    void passesWhenUrlPresent() {
        CitationEvidenceValidator.ValidationResult result = CitationEvidenceValidator.validateSummary(
                "见 https://arxiv.org/abs/2401.00001 的摘要。",
                List.of(new SearchResult("https://arxiv.org/abs/2401.00001", "paper", "summary")));

        assertTrue(result.passed());
    }

    @Test
    void rejectsEmptyToolOutputs() {
        CitationEvidenceValidator.ValidationResult result =
                CitationEvidenceValidator.validateToolOutputs("", List.of());

        assertFalse(result.passed());
    }
}
