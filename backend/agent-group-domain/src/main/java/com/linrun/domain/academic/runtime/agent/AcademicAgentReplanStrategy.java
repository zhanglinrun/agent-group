package com.linrun.domain.academic.runtime.agent;

import java.util.List;

@FunctionalInterface
public interface AcademicAgentReplanStrategy {

    List<AcademicPlanStep> replan(AcademicAgentFlowReplanRequest request);
}















