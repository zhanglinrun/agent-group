package com.linrun.domain.academic.runtime.agent;

@FunctionalInterface
public interface AcademicReActActionExecutor {

    AcademicReActObservation act(AcademicReActDecision decision,
                                 AcademicReActExecutionContext context);
}
