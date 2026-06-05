export interface McpCacheStatusSource {
  cacheStatus?: unknown;
  cacheAgeSeconds?: unknown;
}

export const MCP_CACHE_STATUS_LABELS: Record<string, string> = {
  empty: "未缓存",
  fresh: "缓存有效",
  unbounded: "长期有效",
  expired: "缓存过期",
  disabled: "已停用"
};

export function formatMcpCacheAge(seconds: unknown): string {
  const value = Number(seconds || 0);
  if (!Number.isFinite(value) || value <= 0) return "";
  if (value < 60) return `${Math.floor(value)} 秒`;
  if (value < 3600) return `${Math.floor(value / 60)} 分钟`;
  return `${Math.floor(value / 3600)} 小时`;
}

export function mcpCacheStatusText(server: McpCacheStatusSource | null | undefined): string {
  const status = String(server?.cacheStatus || "empty");
  const label = MCP_CACHE_STATUS_LABELS[status] || status;
  const age = formatMcpCacheAge(server?.cacheAgeSeconds);
  return age ? `${label} · ${age}` : label;
}
