package com.linrun.domain.academic.runtime.agent;

@FunctionalInterface
public interface AcademicReActReasoner {

    AcademicReActDecision think(AcademicReActExecutionContext context);
}
