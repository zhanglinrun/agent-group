package com.linrun.domain.quality.adapter;

import com.linrun.domain.quality.model.GuideEvaluationReport;

import java.util.Optional;

public interface GuideEvaluationReportRepository {

    void save(GuideEvaluationReport report);

    Optional<GuideEvaluationReport> queryLatest();
}
