package com.linrun.domain.quality.adapter;

import com.linrun.domain.quality.model.GuideEvaluationCase;

import java.util.List;

public interface GuideEvaluationCaseRepository {

    List<GuideEvaluationCase> queryEnabledCases();
}
