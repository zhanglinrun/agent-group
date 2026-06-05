package com.linrun.domain.academic.runtime.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AcademicToolRuntimeSummary(int totalCount,
                                         int enabledCount,
                                         int disabledCount,
                                         List<String> registeredToolNames,
                                         List<String> enabledToolNames,
                                         List<String> disabledToolNames,
                                         List<String> missingExpectedToolNames,
                                         Map<String, Integer> categoryCounts,
                                         Map<String, Integer> sourceCounts) {

    public AcademicToolRuntimeSummary {
        totalCount = Math.max(0, totalCount);
        enabledCount = Math.max(0, enabledCount);
        disabledCount = Math.max(0, disabledCount);
        registeredToolNames = registeredToolNames == null ? List.of() : List.copyOf(registeredToolNames);
        enabledToolNames = enabledToolNames == null ? List.of() : List.copyOf(enabledToolNames);
        disabledToolNames = disabledToolNames == null ? List.of() : List.copyOf(disabledToolNames);
        missingExpectedToolNames = missingExpectedToolNames == null
                ? List.of()
                : List.copyOf(missingExpectedToolNames);
        categoryCounts = copyCounts(categoryCounts);
        sourceCounts = copyCounts(sourceCounts);
    }

    public boolean coversAllExpectedTools() {
        return missingExpectedToolNames.isEmpty();
    }

    private static Map<String, Integer> copyCounts(Map<String, Integer> counts) {
        return counts == null ? Map.of() : new LinkedHashMap<>(counts);
    }
}
