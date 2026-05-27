package com.linrun.domain.agent.quality.adapter;

import com.linrun.domain.agent.quality.model.GuideEvaluationCase;

import java.util.List;

public interface GuideEvaluationCaseRepository {

    List<GuideEvaluationCase> queryEnabledCases();
}
