package com.linrun.domain.evaluate.adapter;

import com.linrun.domain.evaluate.model.GuideEvaluationCase;

import java.util.List;

public interface GuideEvaluationCaseRepository {

    List<GuideEvaluationCase> queryEnabledCases();
}
