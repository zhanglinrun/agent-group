package com.linrun.domain.academic.runtime.tool.mcp;

import java.time.LocalDateTime;

public record AcademicMcpCacheStatus(String serverId,
                                     boolean serverEnabled,
                                     int toolCount,
                                     LocalDateTime discoveredAt,
                                     long cacheAgeSeconds,
                                     Long cacheTtlSeconds,
                                     String cacheStatus) {

    public static final String STATUS_EMPTY = "empty";
    public static final String STATUS_FRESH = "fresh";
    public static final String STATUS_UNBOUNDED = "unbounded";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_DISABLED = "disabled";

    public AcademicMcpCacheStatus {
        serverId = serverId == null ? "" : serverId.trim();
        toolCount = Math.max(0, toolCount);
        cacheAgeSeconds = Math.max(0, cacheAgeSeconds);
        cacheStatus = cacheStatus == null ? STATUS_EMPTY : cacheStatus.trim();
    }

    public boolean refreshRequired() {
        return serverEnabled && (STATUS_EMPTY.equals(cacheStatus) || STATUS_EXPIRED.equals(cacheStatus));
    }
}
