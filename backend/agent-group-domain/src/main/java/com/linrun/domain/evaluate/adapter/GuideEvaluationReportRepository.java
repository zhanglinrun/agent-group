package com.linrun.domain.evaluate.adapter;

import com.linrun.domain.evaluate.model.GuideEvaluationReport;

import java.util.Optional;

public interface GuideEvaluationReportRepository {

    void save(GuideEvaluationReport report);

    Optional<GuideEvaluationReport> queryLatest();
}
