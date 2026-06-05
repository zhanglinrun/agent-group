package com.linrun.domain.academic.runtime.tool.port;

import java.util.List;
import java.util.Map;

public interface AcademicTradeAuditPort {

    AcademicTradeAuditResult audit(AcademicTradeAuditRequest request);

    record AcademicTradeAuditRequest(String userId,
                                     String orderId,
                                     String teamId,
                                     String keyword,
                                     int recentOrderLimit,
                                     int recentFlowLimit,
                                     boolean includeRecentFlows) {
    }

    record AcademicTradeAuditResult(boolean success,
                                    String summary,
                                    Map<String, Object> snapshot,
                                    List<Map<String, Object>> findings,
                                    Map<String, Object> metadata,
                                    String errorMessage) {
    }
}
