package com.linrun.domain.agent.quality.adapter;

import com.linrun.domain.agent.quality.model.GuideEvaluationReport;

import java.util.Optional;

public interface GuideEvaluationReportRepository {

    void save(GuideEvaluationReport report);

    Optional<GuideEvaluationReport> queryLatest();
}
